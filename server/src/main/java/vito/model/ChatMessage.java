package vito.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public class ChatMessage {
    @NotNull(message = "userId cannot be null")
    @Pattern(regexp = "^[1-9]\\d{0,4}$|^100000$", message = "userId must be between 1 and 100000")
    private String userId;
    @NotNull(message = "username cannot be null")
    @Size(min = 3, max = 20, message = "username must be between 3 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "username must be alphanumeric")
    @JsonProperty("username")
    private String username;
    @NotNull(message = "message cannot be null")
    @Size(min = 1, max = 500, message = "message must be between 1 and 500 characters")
    private String message;
    @NotNull(message = "timestamp cannot be null")
    private Instant timestamp;
    @NotNull(message = "messageType cannot be null")
    private MessageType messageType;

    public ChatMessage() {
    }

    public ChatMessage(String userId, String username, String message, Instant timestamp, MessageType messageType) {
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.messageType = messageType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return username;
    }

    public void setUserName(String userName) {
        this.username = userName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }
}
