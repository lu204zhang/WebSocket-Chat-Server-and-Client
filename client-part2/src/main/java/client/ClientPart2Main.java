package client;

import client.connection.ConnectionPool;
import client.metrics.Metrics;
import client.metrics.PerMessageRecorder;
import client.sender.MessageGenerator;
import client.sender.SenderWorker;
import model.ChatMessage;

import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static client.config.Constants.*;

/**
 * Entry point for the load-test client (Part 2). Runs warmup phase (32 threads × 1000
 * messages)
 * then main phase (50 workers until 500K total messages), and prints
 * performance metrics and per-message statistics.
 */
public class ClientPart2Main {

    public static void main(String[] args) throws Exception {
        long totalStartMs = System.currentTimeMillis();
        Metrics warmupMetrics = new Metrics();
        warmupPhase(warmupMetrics);
        Metrics mainMetrics = new Metrics();
        mainPhase(mainMetrics);
        long totalDurationMs = System.currentTimeMillis() - totalStartMs;
        printPerformanceMetrics(warmupMetrics, mainMetrics, totalDurationMs);
    }

    private static void warmupPhase(Metrics runMetrics) throws Exception {
        System.out.println(
                "Warmup phase: starting (" + WARMUP_THREADS + " threads × " + WARMUP_MESSAGES_PER_THREAD + " msgs)...");
        BlockingQueue<ChatMessage> warmupQueue = new LinkedBlockingQueue<>();
        ConnectionPool warmupPool = new ConnectionPool(HOST, PORT, WARMUP_POOL_SIZE, WARMUP_MAX_PER_ROOM, runMetrics);

        Thread generator = new Thread(new MessageGenerator(WARMUP_MESSAGES, warmupQueue));
        Thread[] workers = new Thread[WARMUP_THREADS];
        for (int i = 0; i < WARMUP_THREADS; i++) {
            workers[i] = new Thread(
                    new SenderWorker(warmupQueue, warmupPool, runMetrics, null, MAX_RETRIES, BACKOFF_DELAY_MS,
                            WARMUP_MESSAGES_PER_THREAD));
        }

        long startMs = System.currentTimeMillis();
        generator.start();
        System.out.println("Warmup phase: pre-warming connection pool (" + WARMUP_POOL_SIZE + " connections)...");
        warmupPool.preWarm(WARMUP_POOL_SIZE, ROOM_COUNT);
        System.out.println("Warmup phase: generator done, starting workers...");
        for (int i = 0; i < workers.length; i++) {
            workers[i].start();
        }
        System.out.println("Warmup phase: workers started, waiting...");
        for (Thread w : workers) {
            w.join();
        }
        long durationMs = System.currentTimeMillis() - startMs;

        long success = runMetrics.getSuccessCount();
        long failure = runMetrics.getFailureCount();
        System.out.println("Warmup phase: " + durationMs + " ms (" + WARMUP_THREADS + " threads × "
                + WARMUP_MESSAGES_PER_THREAD + " msgs = " + WARMUP_MESSAGES + ")");
        System.out
                .println("Warmup - Success: " + success + ", Failure: " + failure + ", Total: " + (success + failure));
    }

    private static void mainPhase(Metrics mainMetrics) throws Exception {
        System.out.println("Main phase: starting (messages=" + MAIN_MESSAGES + ", workers=" + NUM_WORKERS + ")");
        BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>();
        ConnectionPool pool = new ConnectionPool(HOST, PORT, POOL_SIZE, MAX_PER_ROOM, mainMetrics);
        PerMessageRecorder perMessageRecorder = new PerMessageRecorder();

        System.out.println("Main phase: pre-warming connection pool (" + NUM_WORKERS + " connections)...");
        pool.preWarm(NUM_WORKERS, ROOM_COUNT);

        Thread generator = new Thread(new MessageGenerator(MAIN_MESSAGES, messageQueue));
        Thread[] workers = new Thread[NUM_WORKERS];
        for (int i = 0; i < NUM_WORKERS; i++) {
            workers[i] = new Thread(new SenderWorker(messageQueue, pool, mainMetrics, perMessageRecorder,
                    MAX_RETRIES, BACKOFF_DELAY_MS, EXIT_ON_POISON));
        }

        long startMs = System.currentTimeMillis();
        generator.start();
        for (int i = 0; i < workers.length; i++) {
            workers[i].start();
        }
        generator.join();
        for (int i = 0; i < NUM_WORKERS; i++) {
            messageQueue.offer(SenderWorker.POISON);
        }
        for (Thread w : workers) {
            w.join();
        }
        long durationMs = System.currentTimeMillis() - startMs;

        long success = mainMetrics.getSuccessCount();
        long failure = mainMetrics.getFailureCount();
        System.out.println("Main phase - Success: " + success);
        System.out.println("Main phase - Failure: " + failure);
        System.out.println("Main phase - Duration (ms): " + durationMs);
        System.out.println("Main phase - Throughput (msg/s): " + (durationMs > 0 ? (success * 1000L) / durationMs : 0));

        Path csvPath = perMessageRecorder.writeToCsv();
        System.out.println("Per-message metrics written to: " + csvPath.toAbsolutePath());

        Path statsPath = perMessageRecorder.writeStats(durationMs);
        System.out.println("Statistical analysis written to: " + statsPath.toAbsolutePath());
    }

    private static void printPerformanceMetrics(Metrics warmupMetrics, Metrics mainMetrics, long totalDurationMs) {
        long totalSuccess = warmupMetrics.getSuccessCount() + mainMetrics.getSuccessCount();
        long totalFailure = warmupMetrics.getFailureCount() + mainMetrics.getFailureCount();
        long totalConnections = mainMetrics.getConnectionCreatedCount();
        long reconnections = totalConnections - NUM_WORKERS;
        if (reconnections < 0)
            reconnections = 0;
        long overallThroughput = totalDurationMs > 0 ? (totalSuccess * 1000L) / totalDurationMs : 0;

        System.out.println();
        System.out.println("--- Performance Metrics ---");
        System.out.println("Number of successful messages sent: " + totalSuccess);
        System.out.println("Number of failed messages: " + totalFailure);
        System.out.println("Total runtime (wall time): " + totalDurationMs + " ms");
        System.out.println("Overall throughput (messages/second): " + overallThroughput);
        System.out.println(
                "Connection statistics: total connections=" + totalConnections + ", reconnections=" + reconnections);
    }
}
