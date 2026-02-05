package client.connection;

import static client.config.Constants.CONNECTION_TIMEOUT_SECONDS;
import static client.config.Constants.WEBSOCKET_PATH_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import model.ChatMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Single WebSocket connection to /chat/{roomId}. Sends JSON-serialized {@link model.ChatMessage}.
 * Connection is reusable; marked closed only on onClose/onError. Server echoes valid messages
 * and does not close on LEAVE.
 */
public class ConnectionSession {

    private final WebSocketClient client;
    private final CountDownLatch connectedLatch;
    private final ObjectMapper objectMapper;
    private final int roomId;

    private volatile boolean closed;

    public ConnectionSession(String host, int port, int roomId) throws Exception {
        this.roomId = roomId;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.connectedLatch = new CountDownLatch(1);
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
     * Sends a message on this connection. Serializes to JSON and sends over WebSocket.
     *
     * @param msg the message to send (JOIN, TEXT, or LEAVE)
     * @throws IllegalStateException if connection is closed
     * @throws Exception if serialization or send fails
     */
    public void send(ChatMessage msg) throws Exception {
        if (closed || !client.isOpen()) {
            throw new IllegalStateException("Connection is closed");
        }
        String json = objectMapper.writeValueAsString(msg);
        client.send(json);
    }

    /** Returns true if this connection is still open and usable for sending. */
    public boolean isOpen() {
        return !closed && client.isOpen();
    }

    /**
     * Blocks until the connection is open (or timeout). Call before first send when
     * constructing a new session.
     *
     * @throws InterruptedException if interrupted while waiting
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
