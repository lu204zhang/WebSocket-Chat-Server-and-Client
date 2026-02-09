package model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/**
 * Chat message DTO for WebSocket JSON. Sent to server.
 */
public class ChatMessage {
    private String userId;
    private String username;
    private String message;
    private Instant timestamp;
    private MessageType messageType;

    @JsonIgnore
    private int roomId;

    /**
     * @param userId      user id (1–100000 per assignment)
     * @param username    display name (e.g. "user" + userId)
     * @param message     body (1–500 chars)
     * @param timestamp   ISO-8601
     * @param messageType TEXT, JOIN, or LEAVE
     * @param roomId      room for routing; not sent in JSON
     */
    public ChatMessage(String userId, String username, String message, Instant timestamp, MessageType messageType,
            int roomId) {
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.roomId = roomId;
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

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }
}
