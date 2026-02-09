package model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Server echo format (matches webChat ServerResponse). One response per sent
 * message.
 */
public class ServerResponse {
    private final String status;
    private final Instant serverTimestamp;
    private final String message;

    @JsonCreator
    public ServerResponse(
            @JsonProperty("status") String status,
            @JsonProperty("serverTimestamp") Instant serverTimestamp,
            @JsonProperty("message") String message) {
        this.status = status;
        this.serverTimestamp = serverTimestamp;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public Instant getServerTimestamp() {
        return serverTimestamp;
    }

    public String getMessage() {
        return message;
    }
}
