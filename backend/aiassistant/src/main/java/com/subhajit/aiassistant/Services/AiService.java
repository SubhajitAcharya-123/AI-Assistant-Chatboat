package com.subhajit.aiassistant.Services;


import com.subhajit.aiassistant.Entities.Message;
import com.subhajit.aiassistant.Repository.MessageRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList; // Fix 1: Added missing import
import java.util.List;

@Service
public class AiService {

    private final MessageRepository messageRepository;
    private final ChatClient chatClient;

    public AiService(
            ChatClient.Builder builder,
            MessageRepository messageRepository
    ) {
        this.chatClient = builder.build();
        this.messageRepository = messageRepository;
    }

    public String generateResponse(Long sessionId, String prompt) {
        try {
            List<Message> history = messageRepository.findByChatSessionIdOrderByIdAsc(sessionId);

            // Clean up the non-streaming side to match your new structural design pattern too!
            List<org.springframework.ai.chat.messages.Message> systemMessages = new ArrayList<>();
            for (Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    systemMessages.add(new UserMessage(msg.getContent()));
                } else {
                    systemMessages.add(new AssistantMessage(msg.getContent()));
                }
            }
            systemMessages.add(new UserMessage(prompt));

            return chatClient.prompt()
                    .messages(systemMessages)
                    .call()
                    .content();

        } catch (Exception e) {
            return "Sorry, Gemini is currently unavailable. Please try again in a few moments.";
        }
    }

    public Flux<String> streamResponse(Long sessionId, String prompt) {
        try {
            List<Message> history = messageRepository.findByChatSessionIdOrderByIdAsc(sessionId);

            // Fix 2: Explicitly use full package name declaration for the Spring AI collection
            List<org.springframework.ai.chat.messages.Message> systemMessages = new ArrayList<>();

            for (Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    systemMessages.add(new UserMessage(msg.getContent()));
                } else {
                    systemMessages.add(new AssistantMessage(msg.getContent()));
                }
            }

            systemMessages.add(new UserMessage(prompt));

            return chatClient.prompt()
                    .messages(systemMessages)
                    .stream()
                    .content();

        } catch (Exception e) {
            throw new RuntimeException("Streaming execution broke: " + e.getMessage(), e);
        }
    }
}