package com.subhajit.aiassistant.Controllers;

import com.subhajit.aiassistant.Entities.ChatRequest;
import com.subhajit.aiassistant.Entities.ChatSession;
import com.subhajit.aiassistant.Entities.Message;
import com.subhajit.aiassistant.Repository.ChatSessionRepository;
import com.subhajit.aiassistant.Repository.MessageRepository;
import com.subhajit.aiassistant.Services.AiService;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final AiService aiService;
    private final ChatSessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    public ChatController(
            AiService aiService,
            ChatSessionRepository sessionRepository,
            MessageRepository messageRepository
    ){
        this.aiService = aiService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }
    @PostMapping
    public String chat(@RequestBody ChatRequest request) {

        ChatSession session =
                sessionRepository.findById(request.getSessionId())
                        .orElseThrow();

        if ("New Chat".equals(session.getTitle())) {

            String title = request.getMessage();

            if (title.length() > 30) {
                title = title.substring(0, 30);
            }

            session.setTitle(title);

            sessionRepository.save(session);
        }
        Message userMessage = new Message();

        userMessage.setRole("user");
        userMessage.setContent(request.getMessage());
        userMessage.setChatSession(session);

        messageRepository.save(userMessage);

        String aiResponse =
                aiService.generateResponse(
                        request.getSessionId(),
                        request.getMessage()
                );

        Message aiMessage = new Message();

        aiMessage.setRole("assistant");
        aiMessage.setContent(aiResponse);
        aiMessage.setChatSession(session);

        messageRepository.save(aiMessage);

        return aiResponse;
    }
    @GetMapping("/session/{sessionId}")
    public List<Message> getMessages(
            @PathVariable Long sessionId) {

        return messageRepository.findByChatSessionId(sessionId);
    }

}
