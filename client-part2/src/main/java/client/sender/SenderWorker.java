package client.sender;

import client.connection.ConnectionPool;
import client.connection.ConnectionSession;
import client.connection.SendResult;
import client.metrics.Metrics;
import client.metrics.PerMessageRecorder;
import model.ChatMessage;
import model.MessageType;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;

import static client.config.Constants.*;

/**
 * Consumer thread: takes messages from the queue, borrows a connection by
 * roomId, sends,
 * returns the session, and records success/failure. Uses exponential backoff on
 * retry.
 * Stops when it receives POISON or after maxMessagesToSend.
 */
public class SenderWorker implements Runnable {

    /**
     * Poison pill: when taken from the queue, worker exits and re-offers poison for
     * other workers.
     */
    public static final ChatMessage POISON = new ChatMessage("", "", "", Instant.EPOCH, MessageType.TEXT,
            POISON_ROOM_ID);

    private final BlockingQueue<ChatMessage> messageQueue;
    private final ConnectionPool pool;
    private final Metrics metrics;
    private final PerMessageRecorder perMessageRecorder;
    private final int maxRetries;
    private final long baseBackoffMs;
    private final int maxMessagesToSend;

    /**
     * @param messageQueue       queue to take messages from (and to offer POISON
     *                           back on exit)
     * @param pool               connection pool to borrow/return sessions
     * @param metrics            records success/failure
     * @param perMessageRecorder optional; when non-null, use sendSync and record
     *                           per-message metrics for Part 3 CSV
     * @param maxRetries         max send attempts per message before recording
     *                           failure
     * @param baseBackoffMs      base delay for exponential backoff between retries
     * @param maxMessagesToSend  max messages to send per worker (0 = run until
     *                           poison)
     */
    public SenderWorker(BlockingQueue<ChatMessage> messageQueue, ConnectionPool pool, Metrics metrics,
            PerMessageRecorder perMessageRecorder, int maxRetries, long baseBackoffMs, int maxMessagesToSend) {
        this.messageQueue = messageQueue;
        this.pool = pool;
        this.metrics = metrics;
        this.perMessageRecorder = perMessageRecorder;
        this.maxRetries = maxRetries;
        this.baseBackoffMs = baseBackoffMs;
        this.maxMessagesToSend = maxMessagesToSend;
    }

    /**
     * Takes messages from the queue, sends each via the pool, and stops on POISON
     * or after maxMessagesToSend.
     */
    @Override
    public void run() {
        try {
            int sent = 0;
            while (maxMessagesToSend <= 0 || sent < maxMessagesToSend) {
                ChatMessage msg = messageQueue.take();
                if (msg == POISON) {
                    messageQueue.offer(POISON);
                    break;
                }
                processOne(msg);
                sent++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void processOne(ChatMessage msg) {
        int roomId = msg.getRoomId();
        ConnectionSession session = null;
        long lastSendTimeMs = System.currentTimeMillis();
        String lastStatus = "TIMEOUT";
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (session == null || !session.isOpen())
                    session = pool.borrow(roomId);
                if (perMessageRecorder != null) {
                    SendResult result = session.sendSync(msg);
                    lastSendTimeMs = result.getSendTimeMs();
                    lastStatus = result.getStatus();
                    pool.returnSession(session);
                    if (result.isSuccess()) {
                        long latencyMs = result.getAckTimeMs() - result.getSendTimeMs();
                        perMessageRecorder.record(result.getSendTimeMs(), result.getAckTimeMs(), msg.getMessageType(),
                                latencyMs, STATUS_CODE_OK, roomId);
                        metrics.recordSuccess();
                        return;
                    }
                    // TIMEOUT or ERROR: retry
                } else {
                    session.send(msg);
                    pool.returnSession(session);
                    metrics.recordSuccess();
                    return;
                }
            } catch (Exception e) {
                lastSendTimeMs = System.currentTimeMillis();
                lastStatus = "TIMEOUT";
                if (session != null && !session.isOpen()) {
                    try {
                        pool.returnSession(session);
                    } catch (Exception ignored) {
                    }
                    session = null;
                }
                if (attempt < maxRetries - 1) {
                    try {
                        long delayMs = baseBackoffMs * (long) Math.pow(BACKOFF_BASE, attempt);
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        }
        if (session != null) {
            try {
                pool.returnSession(session);
            } catch (Exception ignored) {
            }
        }
        if (perMessageRecorder != null) {
            perMessageRecorder.record(lastSendTimeMs, -1, msg.getMessageType(), -1, statusToCode(lastStatus), roomId);
        }
        metrics.recordFailure();
    }

    private static int statusToCode(String status) {
        if ("OK".equals(status))
            return STATUS_CODE_OK;
        if ("ERROR".equals(status))
            return STATUS_CODE_ERROR;
        if ("TIMEOUT".equals(status))
            return STATUS_CODE_TIMEOUT;
        return STATUS_CODE_ERROR;
    }
}
