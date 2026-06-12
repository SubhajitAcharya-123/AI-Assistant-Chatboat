package com.subhajit.aiassistant.Services;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SpringAiTestService {

    private final ChatClient chatClient;

    public SpringAiTestService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String test() {

        return chatClient.prompt()
                .user("Say hello")
                .call()
                .content();
    }
}