package client.config;

public final class Constants {

    private Constants() {
    }

    // --- Network ---
    public static final String HOST = "98.81.116.70";
    public static final int PORT = 8080;

    // --- Rooms ---
    public static final int ROOM_COUNT = 20;
    public static final int ROOM_ID_FIRST = 1;

    // --- Warmup phase ---
    public static final int WARMUP_THREADS = 32;
    public static final int WARMUP_MESSAGES_PER_THREAD = 1000;
    public static final int WARMUP_MAX_PER_ROOM = 2;
    public static final int WARMUP_MESSAGES = WARMUP_MESSAGES_PER_THREAD * WARMUP_THREADS;
    public static final int WARMUP_POOL_SIZE = WARMUP_THREADS;

    // --- Main phase ---
    public static final int TOTAL_MESSAGES = 500_000;
    public static final int MAIN_MESSAGES = TOTAL_MESSAGES - WARMUP_MESSAGES;
    public static final int NUM_WORKERS = 400;
    public static final int MAX_PER_ROOM = 20;
    public static final int POOL_SIZE = ROOM_COUNT * MAX_PER_ROOM;

    // --- Retry / backoff ---
    public static final int MAX_RETRIES = 5;
    public static final long BACKOFF_DELAY_MS = 1000;

    // --- SenderWorker ---
    public static final int POISON_ROOM_ID = -1;
    public static final int EXIT_ON_POISON = 0;
    public static final long PROGRESS_LOG_INTERVAL = 50_000;
    public static final int BACKOFF_BASE = 2;

    // --- MessageGenerator ---
    public static final int USER_ID_MAX = 100_000;
    public static final int MESSAGE_PERCENT_TEXT = 90;
    public static final int MESSAGE_PERCENT_JOIN = 5;
    public static final int PERCENT_DENOMINATOR = 100;

    // --- ConnectionSession ---
    public static final int CONNECTION_TIMEOUT_SECONDS = 5;
    public static final String WEBSOCKET_PATH_PREFIX = "/chat/";
    /** Timeout when waiting for server ack in sendSync (ms). */
    public static final long ACK_TIMEOUT_MS = 5000;

    // --- Status codes for CSV (Part 3) ---
    public static final int STATUS_CODE_OK = 200;
    public static final int STATUS_CODE_ERROR = 400;
    public static final int STATUS_CODE_TIMEOUT = 504;

    // --- Results / CSV (Part 3) ---
    public static final String RESULTS_DIR = "results";
    public static final String PER_MESSAGE_CSV = "per_message_metrics.csv";
    public static final String STATS_TXT = "statistical_analysis.txt";
}
