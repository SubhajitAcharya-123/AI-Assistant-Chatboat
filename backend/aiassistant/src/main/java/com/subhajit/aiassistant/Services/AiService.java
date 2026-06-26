package com.subhajit.aiassistant.Services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.time.Duration;

import com.subhajit.aiassistant.DTO.FileUploadResult;
import reactor.core.publisher.Mono;
import org.springframework.ai.chat.messages.Message;
import com.subhajit.aiassistant.Repository.MessageRepository;
import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private final MessageRepository messageRepository;
    private final ChatClient chatClient;
    private final Tika tika = new Tika();
    private final Cloudinary cloudinary;
    private final String cloudinaryFolder;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    public AiService(
            ChatClient.Builder builder,
            MessageRepository messageRepository,
            EmbeddingModel embeddingModel,
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret,
            @Value("${cloudinary.folder-name}") String folderName
    ) {
        this.chatClient = builder.build();
        this.messageRepository = messageRepository;
        this.embeddingModel = embeddingModel;
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        this.cloudinaryFolder = folderName;
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }

    public String generateResponse(Long sessionId, String prompt) {
        try {
            List<com.subhajit.aiassistant.Entities.Message> history = messageRepository.findByChatSessionIdOrderByIdAsc(sessionId);

            List<org.springframework.ai.chat.messages.Message> systemMessages = new ArrayList<>();
            for (com.subhajit.aiassistant.Entities.Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    systemMessages.add(new UserMessage(msg.getContent()));
                } else {
                    systemMessages.add(new AssistantMessage(msg.getContent()));
                }
            }

            // Scan the vector database for global context matching the prompt
            String globalContextMemory = searchGlobalMemory(prompt);
            String finalPrompt = prompt;

            if (!globalContextMemory.isEmpty()) {
                finalPrompt = "Use this context from past conversations/files if relevant to answer:\n"
                        + globalContextMemory + "\n\nUser Question: " + prompt;
            }

            // --- FIXED: Only add the finalPrompt once, do not duplicate with raw prompt ---
            systemMessages.add(new UserMessage(finalPrompt));

            return chatClient.prompt()
                    .messages(systemMessages)
                    .toolNames("currentWeatherFunction")
                    .call()
                    .content();
        } catch (Exception e) {
            System.err.println("Blocking generation error: " + e.getMessage());
            return "Sorry, Gemini is currently unavailable. Please try again in a few moments.";
        }
    }

    public Flux<String> streamResponse(Long sessionId, String prompt) {
        try {
            List<com.subhajit.aiassistant.Entities.Message> history = messageRepository.findByChatSessionIdOrderByIdAsc(sessionId);

            List<org.springframework.ai.chat.messages.Message> systemMessages = new ArrayList<>();
            for (com.subhajit.aiassistant.Entities.Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    systemMessages.add(new UserMessage(msg.getContent()));
                } else {
                    systemMessages.add(new AssistantMessage(msg.getContent()));
                }
            }

            // --- FIXED: Added the global cross-session vector memory scanner to the streaming pipeline ---
            String globalContextMemory = searchGlobalMemory(prompt);
            String finalPrompt = prompt;

            if (!globalContextMemory.isEmpty()) {
                finalPrompt = "Use this context from past conversations/files if relevant to answer:\n"
                        + globalContextMemory + "\n\nUser Question: " + prompt;
            }

            // Inject the unified contextual prompt to the stream request
            systemMessages.add(new UserMessage(finalPrompt));

            return chatClient.prompt()
                    .messages(systemMessages)
                    .toolNames("currentWeatherFunction")
                    .stream()
                    .content()
                    .timeout(Duration.ofSeconds(12))
                    .onErrorResume(throwable -> {
                        System.err.println("❌ Intercepted streaming failure: " + throwable.getMessage());
                        String userFriendlyMessage;

                        if (throwable instanceof java.util.concurrent.TimeoutException) {
                            userFriendlyMessage = "⏳ [Gemini is busy]: The AI server is taking too long to respond right now. Please try sending your message again in a moment.";
                        } else if (throwable.getMessage() != null && throwable.getMessage().contains("429")) {
                            userFriendlyMessage = "⚠️ [Rate Limit Reached]: Too many requests have been sent to the Gemini free tier. Please wait about 30 seconds before trying again.";
                        } else {
                            userFriendlyMessage = "❌ [Service Unavailable]: Gemini is currently busy or experiencing a temporary hiccup. Please try again shortly!";
                        }

                        // Return the message as a safe single-item Flux chunk for the frontend to render smoothly
                        return Flux.just(userFriendlyMessage);
                    });
        } catch (Exception e) {
            System.err.println("Streaming execution broke: " + e.getMessage());
            return Flux.just("❌ An internal server error occurred. Please try again.");
        }
    }
    public FileUploadResult handleFileUploadAndGetContent(MultipartFile file) {

        try {

            String mimeType = file.getContentType();

            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", cloudinaryFolder,
                            "resource_type", "auto"
                    )
            );

            String cloudinaryUrl =
                    (String) uploadResult.get("secure_url");

            if (mimeType != null && mimeType.startsWith("image/")) {

                return new FileUploadResult(
                        cloudinaryUrl,
                        mimeType,
                        file.getOriginalFilename(),
                        "",
                        true
                );
            }

            String extractedText =
                    tika.parseToString(file.getInputStream());
            System.out.println("Cloudinary URL = " + cloudinaryUrl);
            System.out.println("Mime Type = " + mimeType);
            return new FileUploadResult(
                    cloudinaryUrl,
                    mimeType,
                    file.getOriginalFilename(),
                    extractedText,
                    false
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public String generateMultimodalResponse(Long sessionId, UserMessage multimodalMessage) {
        try {
            List<com.subhajit.aiassistant.Entities.Message> history = messageRepository.findByChatSessionIdOrderByIdAsc(sessionId);
            List<org.springframework.ai.chat.messages.Message> systemMessages = new ArrayList<>();

            // Load old chat history
            for (com.subhajit.aiassistant.Entities.Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    systemMessages.add(new UserMessage(msg.getContent()));
                } else {
                    systemMessages.add(new org.springframework.ai.chat.messages.AssistantMessage(msg.getContent()));
                }
            }

            // Append our multimodal image message at the very end
            systemMessages.add(multimodalMessage);

            return chatClient.prompt()
                    .messages(systemMessages)
                    .call()
                    .content();
        } catch (Exception e) {
            return "Error analyzing image content details: " + e.getMessage();
        }
    }
    public void indexDocumentIntoVectorStore(String textContent, String fileName, Long sessionId) {
        if (textContent == null || textContent.trim().isEmpty()) return;

        // 1. Wrap the raw text into a Spring AI Document wrapper
        Document rawDocument = new Document(textContent, Map.of(
                "fileName", fileName,
                "sessionId", sessionId
        ));

        // 2. Split large text into smaller chunks so vector searches stay highly accurate
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitChunks = splitter.apply(List.of(rawDocument));

        // 3. Generate mathematical vectors and save them into the database store
        this.vectorStore.accept(splitChunks);
        System.out.println("✅ Successfully indexed " + splitChunks.size() + " text chunks into Global Vector Store Memory.");
    }
    public String searchGlobalMemory(String query) {
        // Search the vector database for the top 3 closest matching file segments
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(3)
                .similarityThreshold(0.5)
                .build();
        List<Document> similarDocuments = this.vectorStore.similaritySearch(searchRequest);

        if (similarDocuments.isEmpty()) {
            return "";
        }

        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("\n=== RETRIEVED HISTORICAL GLOBAL CONTEXT INTERFACE ===\n");
        for (Document doc : similarDocuments) {
            contextBuilder.append("Source File [").append(doc.getMetadata().get("fileName")).append("]:\n");
            contextBuilder.append(doc.getText()).append("\n---\n");
        }
        return contextBuilder.toString();
    }
}