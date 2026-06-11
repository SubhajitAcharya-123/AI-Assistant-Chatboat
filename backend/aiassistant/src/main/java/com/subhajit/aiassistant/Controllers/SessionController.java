package com.subhajit.aiassistant.Controllers;

import com.subhajit.aiassistant.Entities.ChatSession;
import com.subhajit.aiassistant.Repository.ChatSessionRepository;
import com.subhajit.aiassistant.Repository.MessageRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ChatSessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    public SessionController(ChatSessionRepository sessionRepository,
                             MessageRepository messageRepository
                             ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
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
    @Transactional
    @DeleteMapping("/{id}")
    public void deleteSession(
            @PathVariable Long id
    ) {
        messageRepository.deleteByChatSessionId(id);
        sessionRepository.deleteById(id);
    }
}
