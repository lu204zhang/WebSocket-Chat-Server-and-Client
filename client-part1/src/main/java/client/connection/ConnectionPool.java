package client.connection;

import static client.config.Constants.ROOM_ID_FIRST;

import client.metrics.Metrics;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-room WebSocket connection pool. Connections are created on demand and
 * when
 * replenishing after a closed session. Thread-safe: borrow/return can be called
 * from
 * multiple threads. Optionally reports connection creation to {@link Metrics}.
 */
public class ConnectionPool {
    private final String host;
    private final int port;
    private final int poolSize;
    private final int maxPerRoom;
    private final Metrics metrics;
    private final AtomicInteger totalCount;
    private final Map<Integer, AtomicInteger> roomCounts;
    private final Map<Integer, BlockingQueue<ConnectionSession>> availableByRoom;

    /**
     * Creates a pool without metrics reporting.
     *
     * @param host       server host
     * @param port       server port
     * @param poolSize   max total connections across all rooms
     * @param maxPerRoom max connections per room id
     */
    public ConnectionPool(String host, int port, int poolSize, int maxPerRoom) {
        this(host, port, poolSize, maxPerRoom, null);
    }

    /**
     * Creates a pool; if metrics is non-null, each new connection is reported via
     * {@link Metrics#recordConnectionCreated()}.
     *
     * @param host       server host
     * @param port       server port
     * @param poolSize   max total connections across all rooms
     * @param maxPerRoom max connections per room id
     * @param metrics    optional; connection creation is reported here when
     *                   non-null
     */
    public ConnectionPool(String host, int port, int poolSize, int maxPerRoom, Metrics metrics) {
        this.host = host;
        this.port = port;
        this.poolSize = poolSize;
        this.maxPerRoom = maxPerRoom;
        this.metrics = metrics;
        this.totalCount = new AtomicInteger(0);
        this.roomCounts = new ConcurrentHashMap<>();
        this.availableByRoom = new ConcurrentHashMap<>();
    }

    /**
     * Pre-creates up to targetCount connections, spread across rooms 1..roomCount,
     * respecting maxPerRoom. Call before starting sender threads to avoid
     * cold-start latency.
     *
     * @param targetCount desired number of connections to create
     * @param roomCount   number of rooms to distribute across (room ids from 1 to
     *                    roomCount)
     * @throws Exception if creating a connection fails
     */
    public void preWarm(int targetCount, int roomCount) throws Exception {
        int created = 0;
        int roomId = ROOM_ID_FIRST;
        while (created < targetCount) {
            if (totalCount.get() >= poolSize)
                break;
            if (roomCount(roomId).get() >= maxPerRoom) {
                roomId = (roomId % roomCount) + ROOM_ID_FIRST;
                continue;
            }
            ConnectionSession session = createConnection(roomId);
            queueFor(roomId).offer(session);
            created++;
            roomId = (roomId % roomCount) + ROOM_ID_FIRST;
        }
    }

    /**
     * Acquires a session for the given room: returns an idle open session from the
     * pool,
     * or creates a new connection if under capacity. If the polled session is
     * closed,
     * it is removed and replenished; then retries. Caller must call
     * {@link #returnSession}
     * when done.
     *
     * @param roomId room id (used for WebSocket path and per-room capacity)
     * @return an open ConnectionSession for this room
     * @throws Exception if creation fails or thread is interrupted while waiting
     */
    public ConnectionSession borrow(int roomId) throws Exception {
        BlockingQueue<ConnectionSession> queue = queueFor(roomId);
        for (;;) {
            ConnectionSession session = queue.poll();
            if (session == null) {
                if (totalCount.get() < poolSize && roomCount(roomId).get() < maxPerRoom) {
                    try {
                        return createConnection(roomId);
                    } catch (IllegalStateException e) {
                        session = queue.take();
                    }
                } else {
                    session = queue.take();
                }
            }
            if (session.isOpen()) {
                return session;
            }
            removeAndReplenish(session);
        }
    }

    private BlockingQueue<ConnectionSession> queueFor(int roomId) {
        return availableByRoom.computeIfAbsent(roomId, k -> new LinkedBlockingQueue<>());
    }

    private AtomicInteger roomCount(int roomId) {
        return roomCounts.computeIfAbsent(roomId, k -> new AtomicInteger(0));
    }

    private ConnectionSession createConnection(int roomId) throws Exception {
        if (totalCount.get() >= poolSize || roomCount(roomId).get() >= maxPerRoom) {
            throw new IllegalStateException("pool or room at capacity");
        }
        totalCount.incrementAndGet();
        roomCount(roomId).incrementAndGet();
        try {
            ConnectionSession session = new ConnectionSession(host, port, roomId);
            session.awaitOpen();
            if (metrics != null) {
                metrics.recordConnectionCreated();
            }
            return session;
        } catch (Exception e) {
            totalCount.decrementAndGet();
            roomCount(roomId).decrementAndGet();
            throw e;
        }
    }

    /**
     * Returns a session to the pool. If the session is still open, it is offered
     * back
     * to the room queue; otherwise it is removed and the pool may replenish a new
     * connection.
     *
     * @param session the session previously obtained from {@link #borrow(int)}
     * @throws Exception if replenishment fails
     */
    public void returnSession(ConnectionSession session) throws Exception {
        if (session.isOpen())
            queueFor(session.getRoomId()).offer(session);
        else
            removeAndReplenish(session);
    }

    private void removeAndReplenish(ConnectionSession session) throws Exception {
        int roomId = session.getRoomId();
        if (totalCount.getAndDecrement() <= 0) {
            totalCount.incrementAndGet();
            return;
        }
        roomCount(roomId).decrementAndGet();
        replenish(roomId);
    }

    private void replenish(int roomId) throws Exception {
        if (totalCount.get() >= poolSize || roomCount(roomId).get() >= maxPerRoom)
            return;
        try {
            queueFor(roomId).offer(createConnection(roomId));
        } catch (IllegalStateException ignored) {
        }
    }
}
