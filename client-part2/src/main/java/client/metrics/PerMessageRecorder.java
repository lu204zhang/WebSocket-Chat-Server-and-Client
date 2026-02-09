package client.metrics;

import model.MessageType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static client.config.Constants.PER_MESSAGE_CSV;
import static client.config.Constants.RESULTS_DIR;
import static client.config.Constants.STATUS_CODE_OK;
import static client.config.Constants.STATS_TXT;

/**
 * Records per-message metrics: sendTimestamp, ackTimestamp, messageType,
 * latency, statusCode, roomId. Writes to CSV and writes statistical analysis
 * to a result file.
 */
public class PerMessageRecorder {
    private final ConcurrentLinkedQueue<PerMessageRecord> records = new ConcurrentLinkedQueue<>();

    /**
     * @param sendTimestamp ms before send
     * @param ackTimestamp  ms when ack received, or -1 if not received
     */
    public void record(long sendTimestamp, long ackTimestamp, MessageType messageType, long latencyMs,
            int statusCode, int roomId) {
        records.add(new PerMessageRecord(sendTimestamp, ackTimestamp, messageType, latencyMs, statusCode, roomId));
    }

    public List<PerMessageRecord> getRecords() {
        return new ArrayList<>(records);
    }

    /**
     * Writes all records to results/per_message_metrics.csv. Creates results
     * dir if needed.
     */
    public Path writeToCsv() {
        Path dir = Path.of(RESULTS_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path file = dir.resolve(PER_MESSAGE_CSV);
        List<String> lines = new ArrayList<>();
        lines.add("sendTimestamp,ackTimestamp,messageType,latency,statusCode,roomId");
        for (PerMessageRecord r : records) {
            lines.add(String.format("%d,%d,%s,%d,%d,%d",
                    r.getSendTimestamp(),
                    r.getAckTimestamp(),
                    r.getMessageType(),
                    r.getLatencyMs(),
                    r.getStatusCode(),
                    r.getRoomId()));
        }
        try {
            Files.write(file, lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    /**
     * Computes statistical analysis and writes to results/statistical_analysis.txt:
     * latency (mean, median, 95th, 99th, min, max), throughput per room, and
     * message type distribution.
     *
     * @param totalDurationMs main phase wall time in ms (used for throughput per room)
     * @return path to the written file
     */
    public Path writeStats(long totalDurationMs) {
        Path dir = Path.of(RESULTS_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path file = dir.resolve(STATS_TXT);
        List<String> lines = new ArrayList<>();

        List<PerMessageRecord> all = getRecords();
        if (all.isEmpty()) {
            lines.add("--- Statistical Analysis --- (no records)");
            try {
                Files.write(file, lines);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return file;
        }

        List<Long> latencies = all.stream()
                .mapToLong(PerMessageRecord::getLatencyMs)
                .filter(l -> l >= 0)
                .boxed()
                .toList();

        lines.add("");
        lines.add("--- Statistical Analysis ---");

        if (latencies.isEmpty()) {
            lines.add("Response time (ms): no successful responses");
        } else {
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            int n = sorted.size();
            long sum = sorted.stream().mapToLong(Long::longValue).sum();
            double mean = (double) sum / n;
            long median = sorted.get((n - 1) / 2);
            if (n % 2 == 0)
                median = (median + sorted.get(n / 2)) / 2;
            long p95 = sorted.get((int) Math.min(Math.round(0.95 * (n - 1)), n - 1));
            long p99 = sorted.get((int) Math.min(Math.round(0.99 * (n - 1)), n - 1));
            long min = sorted.get(0);
            long max = sorted.get(n - 1);

            lines.add("Response time (ms) - Mean: " + String.format("%.2f", mean)
                    + ", Median: " + median
                    + ", 95th percentile: " + p95
                    + ", 99th percentile: " + p99
                    + ", Min: " + min
                    + ", Max: " + max);
        }

        double durationSec = totalDurationMs / 1000.0;
        if (durationSec > 0) {
            Map<Integer, Long> successCountByRoom = all.stream()
                    .filter(r -> r.getStatusCode() == STATUS_CODE_OK)
                    .collect(Collectors.groupingBy(PerMessageRecord::getRoomId, Collectors.counting()));
            lines.add("Throughput per room (msg/s):");
            successCountByRoom.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> lines.add("  room " + e.getKey() + ": "
                            + String.format("%.2f", e.getValue() / durationSec)));
        }

        Map<MessageType, Long> countByType = all.stream()
                .collect(Collectors.groupingBy(r -> r.getMessageType(), Collectors.counting()));
        long total = all.size();
        lines.add("Message type distribution:");
        countByType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> lines.add("  " + e.getKey() + ": " + e.getValue()
                        + " (" + String.format("%.1f", 100.0 * e.getValue() / total) + "%)"));

        try {
            Files.write(file, lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }
}
