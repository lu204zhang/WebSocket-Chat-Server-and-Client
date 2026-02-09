package client.sender;

import model.ChatMessage;
import model.MessageType;

import java.time.Instant;

import static client.config.Constants.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

/**
 * Single-thread producer: generates totalCount chat messages with 90% TEXT, 5%
 * JOIN, 5% LEAVE,
 * shuffles by type, then puts them into the given queue. Used by Main to feed
 * sender workers.
 */
public class MessageGenerator implements Runnable {
    private static final String[] MESSAGES = {
            "Hello there!", "How's it going?", "Check this out!", "System update complete.",
            "Lunch at 12?", "See you later.", "Don't forget the milk.", "Got it, thanks!",
            "That's hilarious!", "On my way.", "Can you help me?", "Great job today!",
            "Call me back.", "Happy Birthday!", "What's the plan?", "Low battery warning.",
            "Nice to meet you.", "Let's go!", "I'm bored.", "Wait for me.",
            "Good morning!", "Sweet dreams.", "I'm almost there.", "Where are you?",
            "Yes, please.", "No problem.", "Keep it up!", "Amazing work.",
            "Dinner is ready.", "I'll be late.", "Stay safe.", "Missing you.",
            "Just a moment.", "Look at this!", "I'm so tired.", "Coffee soon?",
            "You're welcome.", "Maybe tomorrow.", "I forgot.", "Keep in touch.",
            "Nice weather today.", "I love this song!", "Best day ever.", "Not sure yet.",
            "Take care.", "Let's grab a drink.", "Sounds good.", "Tell me more.",
            "I'm home.", "Good luck!"
    };

    private final int totalCount;
    private final BlockingQueue<ChatMessage> messageQueue;
    private final Random random;

    /**
     * @param totalCount   total messages to generate and put into the queue
     * @param messageQueue thread-safe queue to put generated messages into
     */
    public MessageGenerator(int totalCount, BlockingQueue<ChatMessage> messageQueue) {
        this.totalCount = totalCount;
        this.messageQueue = messageQueue;
        this.random = new Random();
    }

    private ChatMessage generateMessage(MessageType messageType) {
        String userId = String.valueOf(random.nextInt(USER_ID_MAX) + 1);
        String username = "user" + userId;
        String message = MESSAGES[random.nextInt(MESSAGES.length)];
        int roomId = random.nextInt(ROOM_COUNT) + ROOM_ID_FIRST;
        Instant timestamp = Instant.now();
        return new ChatMessage(userId, username, message, timestamp, messageType, roomId);
    }

    @Override
    public void run() {
        int textCount = totalCount * MESSAGE_PERCENT_TEXT / PERCENT_DENOMINATOR;
        int joinCount = totalCount * MESSAGE_PERCENT_JOIN / PERCENT_DENOMINATOR;
        int leaveCount = totalCount - textCount - joinCount;
        List<MessageType> types = new ArrayList<>(totalCount);
        for (int i = 0; i < textCount; i++)
            types.add(MessageType.TEXT);
        for (int i = 0; i < joinCount; i++)
            types.add(MessageType.JOIN);
        for (int i = 0; i < leaveCount; i++)
            types.add(MessageType.LEAVE);
        Collections.shuffle(types);
        for (MessageType type : types) {
            try {
                messageQueue.put(generateMessage(type));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
