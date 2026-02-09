package client.connection;

/**
 * Result of sendSync: send time, ack time (or -1 if timeout/failure), and
 * server status string ("OK", "ERROR", or "TIMEOUT").
 */
public final class SendResult {
    private final long sendTimeMs;
    private final long ackTimeMs;
    private final String status;

    public SendResult(long sendTimeMs, long ackTimeMs, String status) {
        this.sendTimeMs = sendTimeMs;
        this.ackTimeMs = ackTimeMs;
        this.status = status;
    }

    public long getSendTimeMs() {
        return sendTimeMs;
    }

    /** Client time when ack was received; -1 if timeout or failure. */
    public long getAckTimeMs() {
        return ackTimeMs;
    }

    /** "OK", "ERROR", or "TIMEOUT". */
    public String getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return "OK".equals(status);
    }
}
