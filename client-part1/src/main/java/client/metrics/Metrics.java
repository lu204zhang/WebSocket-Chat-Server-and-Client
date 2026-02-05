package client.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe counters for load-test run: successful sends, failed sends, and
 * total WebSocket connections created. Updated by SenderWorker and ConnectionPool.
 */
public class Metrics {
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong connectionCreatedCount = new AtomicLong(0);

    /** Increments the count of successfully sent messages. */
    public void recordSuccess() {
        successCount.incrementAndGet();
    }

    /** Increments the count of messages that failed after all retries. */
    public void recordFailure() {
        failureCount.incrementAndGet();
    }

    /** Increments the count of WebSocket connections created (including reconnections). */
    public void recordConnectionCreated() {
        connectionCreatedCount.incrementAndGet();
    }

    /** Returns the number of successfully sent messages. */
    public long getSuccessCount() {
        return successCount.get();
    }

    /** Returns the number of failed messages. */
    public long getFailureCount() {
        return failureCount.get();
    }

    /** Returns the total number of connections ever created by the pool(s) using this metrics. */
    public long getConnectionCreatedCount() {
        return connectionCreatedCount.get();
    }
}
