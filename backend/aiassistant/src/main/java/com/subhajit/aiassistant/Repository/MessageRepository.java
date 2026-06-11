package com.subhajit.aiassistant.Repository;

import com.subhajit.aiassistant.Entities.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository
        extends JpaRepository<Message, Long> {
    List<Message> findByChatSessionId(Long sessionId);
    List<Message> findByChatSessionIdOrderByIdAsc(Long sessionId);
    void deleteByChatSessionId(Long sessionId);
}
