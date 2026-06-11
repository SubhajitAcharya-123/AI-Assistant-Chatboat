package com.subhajit.aiassistant.Repository;

import com.subhajit.aiassistant.Entities.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatSessionRepository
        extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findAllByOrderByCreatedAtDesc();
}
