package com.subhajit.aiassistant.Controllers;

import com.subhajit.aiassistant.DTO.FileUploadResult;
import com.subhajit.aiassistant.DTO.UploadResponse;
import com.subhajit.aiassistant.Entities.ChatRequest;
import com.subhajit.aiassistant.Entities.ChatSession;
import com.subhajit.aiassistant.Entities.Message;
import com.subhajit.aiassistant.Repository.ChatSessionRepository;
import com.subhajit.aiassistant.Repository.MessageRepository;
import com.subhajit.aiassistant.Services.AiService;
import org.springframework.ai.content.Media;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import org.springframework.ai.chat.messages.UserMessage;
import org.apache.tika.Tika;
import java.nio.charset.StandardCharsets;
import java.util.List;


@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*") // Allows React to hit your endpoints without CORS blocks
public class ChatController {

    private final AiService aiService;
    private final ChatSessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final Tika tikaParser; // --- FIX 1: Add missing Tika field ---

    public ChatController(
            AiService aiService,
            ChatSessionRepository sessionRepository,
            MessageRepository messageRepository
    ){
        this.aiService = aiService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.tikaParser = new Tika(); // --- FIX 2: Initialize Tika Parser ---
    }

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        ChatSession session = sessionRepository.findById(request.getSessionId()).orElseThrow();

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

        String aiResponse = aiService.generateResponse(request.getSessionId(), request.getMessage());

        Message aiMessage = new Message();
        aiMessage.setRole("assistant");
        aiMessage.setContent(aiResponse);
        aiMessage.setChatSession(session);
        messageRepository.save(aiMessage);

        return aiResponse;
    }

    @GetMapping("/session/{sessionId}")
    public List<Message> getMessages(@PathVariable Long sessionId) {
        return messageRepository.findByChatSessionId(sessionId);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam Long sessionId, @RequestParam String prompt) {
        System.out.println("=== [START] Incoming stream request for session: " + sessionId + " ===");
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

        aiService.streamResponse(sessionId, prompt)
                .doOnNext(chunk -> {
                    try {
                        System.out.print(chunk);
                        System.out.flush();
                        fullResponse.append(chunk);
                        String cleanChunk = chunk.replace("\n", "\\n");
                        emitter.send(SseEmitter.event().comment("flush").data(cleanChunk));
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
                .subscribe();

        return emitter;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse uploadChatContent(
            @RequestParam("file") MultipartFile file,
            @RequestParam("prompt") String prompt,
            @RequestParam("sessionId") Long sessionId
    ) {
        ChatSession session = sessionRepository.findById(sessionId).orElseThrow();
        FileUploadResult uploadResult =
                aiService.handleFileUploadAndGetContent(file);

        // Save incoming user message directly to history DB
        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent( prompt);
        userMessage.setMediaUrl(uploadResult.getMediaUrl());

        userMessage.setMediaType(uploadResult.getMediaType());
        userMessage.setFileName(uploadResult.getFileName());
        userMessage.setChatSession(session);
        messageRepository.save(userMessage);

        String aiResponse = ""; // Initialized once at method scope

        // --- MULTIMODAL IMAGE LOGIC ---
        if (uploadResult.isImage()) {
            try {
                Media imageMedia = new Media(
                        org.springframework.http.MediaType.parseMediaType(file.getContentType()),
                        file.getResource()
                );

                String structuredPrompt = "The user has uploaded an image. Analyze it carefully to answer this question:\n" + prompt;
                UserMessage multimodalMessage = UserMessage.builder()
                        .text(structuredPrompt)
                        .media(imageMedia)
                        .build();

                aiResponse = aiService.generateMultimodalResponse(sessionId, multimodalMessage);

            } catch (Exception e) {
                aiResponse = "Failed to process the image: " + e.getMessage();
            }
        } else {

            String extractedText = uploadResult.getExtractedContent();
//            try {
//                if (file.getOriginalFilename() != null && file.getOriginalFilename().endsWith(".txt")) {
//                    extractedText = new String(file.getBytes(), StandardCharsets.UTF_8);
//                } else {
//                    extractedText = tikaParser.parseToString(file.getInputStream());
//                }
//            } catch (Exception e) {
//                // --- FIX 4: Corrected System.err.println compilation symbol block ---
//                System.err.println("Failed parsing document text: " + e.getMessage());
//            }

            if (extractedText == null || extractedText.trim().isEmpty()) {
                return new UploadResponse(
                        "Uploaded successfully, but no readable text was found in the document.",
                        uploadResult.getMediaUrl(),
                        uploadResult.getMediaType(),
                        uploadResult.getFileName(),
                        uploadResult.getExtractedContent()
                );
            }

            aiService.indexDocumentIntoVectorStore(extractedText, file.getOriginalFilename(), sessionId);

            String contextPrompt = "Context from uploaded file attachment (" + file.getOriginalFilename() + "):\n"
                    + extractedText + "\n\nUser Question: " + prompt;

            aiResponse = aiService.generateResponse(sessionId, contextPrompt);
        }

        Message aiMessage = new Message();
        aiMessage.setRole("assistant");
        aiMessage.setContent(aiResponse);
        aiMessage.setChatSession(session);
        messageRepository.save(aiMessage);

        return new UploadResponse(
                aiResponse,
                uploadResult.getMediaUrl(),
                uploadResult.getMediaType(),
                uploadResult.getFileName(),
                uploadResult.getExtractedContent()
        );
    }

    @GetMapping("/test-vector-search")
    public String testVectorSearch(@RequestParam("query") String query) {
        System.out.println("🔍 Triggering manual similarity vector search for: " + query);
        String result = aiService.searchGlobalMemory(query);

        if (result.isEmpty()) {
            return "Vector store returned 0 results. Either no documents are indexed yet, or no matches were found.";
        }
        return result;
    }
}