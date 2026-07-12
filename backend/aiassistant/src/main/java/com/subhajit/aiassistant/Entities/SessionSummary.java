package com.subhajit.aiassistant.Entities;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class SessionSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // getters setters
}
