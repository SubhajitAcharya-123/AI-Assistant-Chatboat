package com.subhajit.aiassistant.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.subhajit.aiassistant.DTO.GeminiResponse;
import com.subhajit.aiassistant.Entities.Message;
import com.subhajit.aiassistant.Repository.MessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Value("${gemini.api.key}")
    private String apiKey;


    private final MessageRepository messageRepository;

    public AiService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }
    private final RestClient restClient = RestClient.create();

    public String generateResponse(Long sessionId, String prompt) {
        try{
        System.out.println(apiKey);
        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                        + apiKey;
        System.out.println(url);
            List<Message> history = messageRepository.findByChatSessionIdOrderByIdAsc(sessionId);
            StringBuilder context = new StringBuilder();
            for (Message message : history) {

                context.append(message.getRole())
                        .append(": ")
                        .append(message.getContent())
                        .append("\n");
            }
        Map<String, Object> body = Map.of(
                "contents",
                List.of(
                        Map.of(
                                "parts",
                                List.of(
                                        Map.of("text", context.toString())
                                )
                        )
                )
        );

        GeminiResponse response = restClient.post()
                .uri(url)
                .body(body)
                .retrieve()
                .body(GeminiResponse.class);


        return response.candidates()
                .getFirst()
                .content()
                .parts()
                .getFirst()
                .text();

        } catch (Exception e) {

            return "Sorry, Gemini is currently unavailable. Please try again in a few moments.";
        }
    }
}
