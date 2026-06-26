package com.subhajit.aiassistant.Entities;

import jakarta.persistence.*;

import lombok.Data;

@Entity
@Data
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String mediaUrl;
    private String mediaType;
    private String fileName;
    @ManyToOne
    @JoinColumn(name = "chat_session_id")
    private ChatSession chatSession;

}