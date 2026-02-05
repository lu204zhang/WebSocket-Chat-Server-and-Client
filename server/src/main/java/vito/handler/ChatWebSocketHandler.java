package vito.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import vito.model.ChatMessage;
import vito.model.MessageType;
import vito.model.ServerResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import vito.validator.MessageValidator;
import vito.validator.ValidationResult;

import java.time.Instant;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final String JOINED_KEY = "joined";
    private static final String USER_ID_KEY = "userId";
    private final MessageValidator messageValidator;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(MessageValidator messageValidator, ObjectMapper objectMapper) {
        this.messageValidator = messageValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.getAttributes().put(JOINED_KEY, false);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        ChatMessage chatMessage = null;
        // parse the JSON payload into a ChatMessage object
        try {
            chatMessage = objectMapper.readValue(payload, ChatMessage.class);
        } catch (JsonProcessingException e) {
            ServerResponse response = new ServerResponse("ERROR", Instant.now(), "Unable to parse JSON");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            return;
        }
        // validate the ChatMessage object
        ValidationResult result = messageValidator.validate(chatMessage);
        ServerResponse response = null;
        if (!result.isValid()) {
            response = new ServerResponse("ERROR", Instant.now(), result.getMessage());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            return;
        }
        Map<String, Object> attributes = session.getAttributes();
        MessageType messageType = chatMessage.getMessageType();
        switch (messageType) {
            case JOIN:
                attributes.put(JOINED_KEY, true);
                attributes.put(USER_ID_KEY, chatMessage.getUserId());
                response = new ServerResponse("OK", Instant.now(),
                        "You have joined the chat: " + chatMessage.getMessage());
                break;
            case LEAVE:
                response = new ServerResponse("OK", Instant.now(),
                        "You have left the chat: " + chatMessage.getMessage());
                break;
            case TEXT:
                response = new ServerResponse("OK", Instant.now(), chatMessage.getMessage());
                break;
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        session.getAttributes().clear();
    }
}
