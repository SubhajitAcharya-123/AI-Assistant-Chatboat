package com.subhajit.aiassistant.Repository;

import com.subhajit.aiassistant.Entities.SessionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SessionSummaryRepository extends JpaRepository<SessionSummary, Long> {
    Optional<SessionSummary> findTopBySessionIdOrderByIdDesc(Long sessionId);
}