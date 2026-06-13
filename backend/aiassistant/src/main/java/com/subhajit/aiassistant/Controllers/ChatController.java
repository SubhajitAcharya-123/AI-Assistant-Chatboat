package com.subhajit.aiassistant.Controllers;

import com.subhajit.aiassistant.Entities.ChatRequest;
import com.subhajit.aiassistant.Entities.ChatSession;
import com.subhajit.aiassistant.Entities.Message;
import com.subhajit.aiassistant.Repository.ChatSessionRepository;
import com.subhajit.aiassistant.Repository.MessageRepository;
import com.subhajit.aiassistant.Services.AiService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;

import java.util.List;


@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final AiService aiService;
    private final ChatSessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    public ChatController(
            AiService aiService,
            ChatSessionRepository sessionRepository,
            MessageRepository messageRepository
    ){
        this.aiService = aiService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {

        ChatSession session =
                sessionRepository.findById(request.getSessionId())
                        .orElseThrow();

        if ("New Chat".equals(session.getTitle())) {

            String title = request.getMessage();

            if (title.length() > 30) {
                title = title.substring(0, 30);
            }

            session.setTitle(title);

            sessionRepository.save(session);
        }
        Message userMessage = new Message();

        userMessage.setRole("user");
        userMessage.setContent(request.getMessage());
        userMessage.setChatSession(session);

        messageRepository.save(userMessage);

        String aiResponse =
                aiService.generateResponse(
                        request.getSessionId(),
                        request.getMessage()
                );

        Message aiMessage = new Message();

        aiMessage.setRole("assistant");
        aiMessage.setContent(aiResponse);
        aiMessage.setChatSession(session);

        messageRepository.save(aiMessage);

        return aiResponse;
    }
    @GetMapping("/session/{sessionId}")
    public List<Message> getMessages(
            @PathVariable Long sessionId) {

        return messageRepository.findByChatSessionId(sessionId);
    }
    @GetMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(
            @RequestParam Long sessionId,
            @RequestParam String prompt
    ) {
        System.out.println("=== [START] Incoming stream request for session: " + sessionId + " ===");

        // Create an emitter with no timeout (0L) to keep the connection open during long generation tasks
        SseEmitter emitter = new SseEmitter(0L);

        ChatSession session = sessionRepository.findById(sessionId).orElseThrow();

        if ("New Chat".equals(session.getTitle())) {
            String title = prompt.length() > 30 ? prompt.substring(0, 30) : prompt;
            session.setTitle(title);
            sessionRepository.save(session);
        }

        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(prompt);
        userMessage.setChatSession(session);
        messageRepository.save(userMessage);

        StringBuilder fullResponse = new StringBuilder();

        // Subscribe to the reactive Flux stream explicitly and push chunks through the emitter manually
        aiService.streamResponse(sessionId, prompt)
                .doOnNext(chunk -> {
                    try {
                        System.out.print(chunk);
                        System.out.flush();
                        fullResponse.append(chunk);

                        // Escape internal newlines so SSE doesn't interpret them as broken separate events
                        String cleanChunk = chunk.replace("\n", "\\n");

                        // Force a direct flush down the Tomcat network socket instantly
                        emitter.send(SseEmitter.event().data(cleanChunk));
                    } catch (IOException e) {
                        System.out.println("⚠️ Client disconnected early from stream.");
                    }
                })
                .doOnComplete(() -> {
                    try {
                        System.out.println("\n=== [COMPLETE] Stream finished from Gemini. Saving to DB. ===");
                        Message aiMessage = new Message();
                        aiMessage.setRole("assistant");
                        aiMessage.setContent(fullResponse.toString());
                        aiMessage.setChatSession(session);
                        messageRepository.save(aiMessage);

                        // Notify React that the response is complete, then close the emitter channel safely
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(error -> {
            System.out.println("\n❌ [ERROR] Backend Streaming Error: " + error.getMessage());
            emitter.completeWithError(error);
        })
                .subscribe(); // Starts processing the Flux chain asynchronously

        return emitter;
    }
}
