package com.subhajit.aiassistant.Controllers;

import com.subhajit.aiassistant.Entities.ChatSession;
import com.subhajit.aiassistant.Repository.ChatSessionRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ChatSessionRepository sessionRepository;

    public SessionController(ChatSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @PostMapping
    public ChatSession createSession() {

        ChatSession session = new ChatSession();

        session.setTitle("New Chat");
        session.setCreatedAt(LocalDateTime.now());

        return sessionRepository.save(session);
    }
    @GetMapping
    public List<ChatSession> getAllSessions() {

        return sessionRepository
                .findAllByOrderByCreatedAtDesc();
    }
}
