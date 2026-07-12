package com.subhajit.aiassistant.Services;

import com.subhajit.aiassistant.Repository.MessageRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MemoryBackgroundService {

    private final AiService aiService;
    private final MessageRepository messageRepository;

    public MemoryBackgroundService(
            AiService aiService,
            MessageRepository messageRepository
    ) {
        this.aiService = aiService;
        this.messageRepository = messageRepository;
    }

    @Async("memoryExecutor")
    public void processMemory(
            Long sessionId,
            String prompt,
            String response
    ) {

        try {

            aiService.indexChatMemory(
                    sessionId,
                    prompt
            );

            aiService.indexAssistantMemory(
                    sessionId,
                    response
            );

            long count = messageRepository
                            .countByChatSessionId(sessionId);

            if(count % 50 == 0) {
                aiService.summarizeSession(sessionId);
            }

        } catch(Exception e) {
            System.err.println(
                    "Memory processing failed: "
                            + e.getMessage()
            );
        }
    }
}
