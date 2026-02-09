# CS6650 Assignment 1: WebSocket Chat Server and Client

A WebSocket chat server (Spring Boot) and multithreaded Java clients for load testing. The server validates JSON messages and echoes them back; the clients simulate high-volume messaging (warmup + main phase up to 500K messages) and collect performance metrics.

## Prerequisites

- **Java 17** and **Maven 3.6+**

## Project Structure

```
hw01/
├── server/        # WebSocket chat server (Spring Boot, port 8080)
├── client-part1/  # Basic load-test client
├── client-part2/  # Client with per-message latency and statistical analysis
└── results/       # Output: per_message_metrics.csv, statistical_analysis.txt
```

## Running Instructions

### 1. Start the Server

From the project root:

```bash
cd server
mvn spring-boot:run
```

The server listens on **port 8080**.

**Health check:** `curl http://localhost:8080/health`

**Deploy on EC2:** Build with `mvn clean package -DskipTests`, upload `target/webChat-1.0-SNAPSHOT.jar` to the instance, then run `java -jar webChat-1.0-SNAPSHOT.jar`. Ensure the security group allows inbound traffic on port 8080.

---

### 2. Configure the Client

Before running either client, set the server address in the client’s `Constants.java`:

- **client-part1:** `client-part1/src/main/java/client/config/Constants.java`
- **client-part2:** `client-part2/src/main/java/client/config/Constants.java`

Edit:

- `HOST` — server host (e.g. `"localhost"` for local server, or your EC2 public IP)
- `PORT` — server port (default `8080`)

---

### 3. Run Client Part 1 (Basic Load Test)

From the project root:

```bash
cd client-part1
mvn compile
```

Run the main class **`client.Main`** from your IDE (with `client-part1` as the module), or run from the command line with the full classpath. Part 1 does not bundle the exec-maven-plugin; Part 2 does.

**Output:** Warmup phase (32 threads × 1000 messages), then main phase until 500K messages; final performance metrics (success, failure, wall time, throughput, connection stats).

---

### 4. Run Client Part 2 (With Per-Message Metrics and Stats)

From the project root:

```bash
cd client-part2
mvn compile exec:java -Dexec.mainClass=client.ClientPart2Main
```

Or run `client.ClientPart2Main` from your IDE with the `client-part2` module.

**Output:**

- Console: warmup and main phase metrics, then overall performance metrics.
- **results/per_message_metrics.csv** — per-message send/ack timestamps, latency, statusCode, roomId.
- **results/statistical_analysis.txt** — mean/median/P95/P99/min/max latency, throughput per room, message type distribution.

Ensure the server is running and `HOST`/`PORT` in `client-part2` point to it.

---

## Summary

| Step | Command / Action |
|------|------------------|
| Start server | `cd server && mvn spring-boot:run` |
| Set client target | Edit `HOST` and `PORT` in `client-part1` / `client-part2` → `config/Constants.java` |
| Run Part 1 client | `cd client-part1 && mvn compile` then run `client.Main` from IDE |
| Run Part 2 client | `cd client-part2 && mvn compile exec:java -Dexec.mainClass=client.ClientPart2Main` |

Results are written under **results/** (CSV and stats file).
