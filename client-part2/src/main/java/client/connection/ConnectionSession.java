package client.connection;

import static client.config.Constants.ACK_TIMEOUT_MS;
import static client.config.Constants.CONNECTION_TIMEOUT_SECONDS;
import static client.config.Constants.WEBSOCKET_PATH_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import model.ChatMessage;
import model.ServerResponse;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Single WebSocket connection to /chat/{roomId}. Sends JSON-serialized
 * {@link model.ChatMessage}.
 * Connection is reusable; marked closed only on onClose/onError. Server echoes
 * valid messages
 * and does not close on LEAVE.
 */
public class ConnectionSession {

    private final WebSocketClient client;
    private final CountDownLatch connectedLatch;
    private final ObjectMapper objectMapper;
    private final int roomId;
    private final BlockingQueue<ServerResponse> responseQueue;

    private volatile boolean closed;

    public ConnectionSession(String host, int port, int roomId) throws Exception {
        this.roomId = roomId;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.connectedLatch = new CountDownLatch(1);
        this.responseQueue = new LinkedBlockingQueue<>();
        this.closed = false;

        String path = WEBSOCKET_PATH_PREFIX + roomId;
        URI uri = new URI("ws", null, host, port, path, null, null);
        this.client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                connectedLatch.countDown();
            }

            @Override
            public void onMessage(String message) {
                try {
                    ServerResponse response = objectMapper.readValue(message, ServerResponse.class);
                    responseQueue.offer(response);
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                closed = true;
                connectedLatch.countDown();
            }

            @Override
            public void onError(Exception ex) {
                closed = true;
                connectedLatch.countDown();
            }
        };
        client.connect();
    }

    /** Returns the room id this connection is bound to (path /chat/{roomId}). */
    public int getRoomId() {
        return roomId;
    }

    /**
     * Sends a message on this connection. Serializes to JSON and sends over
     * WebSocket.
     *
     * @param msg the message to send (JOIN, TEXT, or LEAVE)
     * @throws IllegalStateException if connection is closed
     * @throws Exception             if serialization or send fails
     */
    public void send(ChatMessage msg) throws Exception {
        if (closed || !client.isOpen()) {
            throw new IllegalStateException("Connection is closed");
        }
        String json = objectMapper.writeValueAsString(msg);
        client.send(json);
    }

    /**
     * Sends a message and blocks until server ack is received (or timeout).
     * Returns send time, ack time, and server status for latency/metrics.
     *
     * @param msg the message to send
     * @return SendResult with sendTimeMs, ackTimeMs (-1 if timeout), and status
     *         ("OK", "ERROR", "TIMEOUT")
     */
    public SendResult sendSync(ChatMessage msg) throws Exception {
        if (closed || !client.isOpen()) {
            throw new IllegalStateException("Connection is closed");
        }
        long sendTimeMs = System.currentTimeMillis();
        String json = objectMapper.writeValueAsString(msg);
        client.send(json);
        ServerResponse response = responseQueue.poll(ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (response == null) {
            return new SendResult(sendTimeMs, -1, "TIMEOUT");
        }
        long ackTimeMs = System.currentTimeMillis();
        return new SendResult(sendTimeMs, ackTimeMs, response.getStatus());
    }

    /** Returns true if this connection is still open and usable for sending. */
    public boolean isOpen() {
        return !closed && client.isOpen();
    }

    /**
     * Blocks until the connection is open (or timeout). Call before first send when
     * constructing a new session.
     *
     * @throws InterruptedException  if interrupted while waiting
     * @throws IllegalStateException if timeout or connection failed/closed
     */
    public void awaitOpen() throws InterruptedException {
        if (!connectedLatch.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Connection timeout (" + CONNECTION_TIMEOUT_SECONDS + "s) to " + client.getURI());
        }
        if (!isOpen()) {
            throw new IllegalStateException("Connection failed or closed");
        }
    }
}
