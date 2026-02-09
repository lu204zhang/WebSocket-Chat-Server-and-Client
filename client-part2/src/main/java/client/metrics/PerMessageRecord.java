package client.metrics;

import model.MessageType;

/**
 * One row for per-message metrics CSV: sendTimestamp (before send), ackTimestamp
 * (when ack received, or -1 if none), messageType, latency, statusCode, roomId.
 */
public final class PerMessageRecord {
    private final long sendTimestamp;
    private final long ackTimestamp;
    private final MessageType messageType;
    private final long latencyMs;
    private final int statusCode;
    private final int roomId;

    public PerMessageRecord(long sendTimestamp, long ackTimestamp, MessageType messageType, long latencyMs,
            int statusCode, int roomId) {
        this.sendTimestamp = sendTimestamp;
        this.ackTimestamp = ackTimestamp;
        this.messageType = messageType;
        this.latencyMs = latencyMs;
        this.statusCode = statusCode;
        this.roomId = roomId;
    }

    /** Timestamp (ms) before send. */
    public long getSendTimestamp() {
        return sendTimestamp;
    }

    /** Timestamp (ms) when acknowledgment received; -1 if not received (timeout/failure). */
    public long getAckTimestamp() {
        return ackTimestamp;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getRoomId() {
        return roomId;
    }
}
