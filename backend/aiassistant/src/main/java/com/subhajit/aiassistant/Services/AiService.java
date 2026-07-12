package com.subhajit.aiassistant.Services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.time.Duration;
import com.subhajit.aiassistant.DTO.FileUploadResult;
import com.subhajit.aiassistant.Entities.Message;
import com.subhajit.aiassistant.Entities.SessionSummary;
import com.subhajit.aiassistant.Repository.MessageRepository;
import com.subhajit.aiassistant.Repository.SessionSummaryRepository;
import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

@Service
public class AiService {

    private final MessageRepository messageRepository;
    private final ChatClient chatClient;
    private final Tika tika = new Tika();
    private final Cloudinary cloudinary;
    private final String cloudinaryFolder;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final SessionSummaryRepository sessionSummaryRepository;

    // 🔥 PRODUCTION PATTERN: ThreadLocal tracks active session context across autonomous tool chains safely
    private static final ThreadLocal<Long> currentSessionContext = new ThreadLocal<>();

    public AiService(
            ChatClient.Builder builder,
            MessageRepository messageRepository,
            EmbeddingModel embeddingModel,
            VectorStore vectorStore,
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret,
            @Value("${cloudinary.folder-name}") String folderName,
            SessionSummaryRepository sessionSummaryRepository
    ) {
        this.chatClient = builder.build();
        this.messageRepository = messageRepository;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.cloudinaryFolder = folderName;
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
        this.sessionSummaryRepository = sessionSummaryRepository;
    }

    public String generateResponse(Long sessionId, String prompt) {
        try {
            currentSessionContext.set(sessionId); // Set target thread workspace identifier
            Optional<SessionSummary> summary = sessionSummaryRepository.findTopBySessionIdOrderByIdDesc(sessionId);
            List<Message> history = messageRepository.findTop20ByChatSessionIdOrderByIdDesc(sessionId);
            Collections.reverse(history);

            List<org.springframework.ai.chat.messages.Message> systemMessages = new ArrayList<>();
            systemMessages.add(new SystemMessage(getSystemInstructionContext()));

            if (summary.isPresent() && summary.get().getSummary() != null && !summary.get().getSummary().isBlank()) {
                systemMessages.add(new SystemMessage("Previous conversation historical context summary:\n" + summary.get().getSummary()));
            }

            for (Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    systemMessages.add(new UserMessage(msg.getContent()));
                } else {
                    systemMessages.add(new AssistantMessage(msg.getContent()));
                }
            }

            String retrievalContext = buildRetrievalContext(prompt);

            String finalPrompt = retrievalContext.isBlank() ? prompt : retrievalContext + "\n\nUser Question:\n" + prompt;

            systemMessages.add(new UserMessage(finalPrompt));

            return chatClient.prompt()
                    .messages(systemMessages)
                    .toolNames("currentWeatherFunction", "currentTimeTool", "memorySearchTool", "searchDocumentTool", "executeCalculator", "searchWebTool")
                    .call()
                    .content();
        } finally {
            currentSessionContext.remove(); // Prevent background context leaks
        }
    }

    public Flux<String> streamResponse(Long sessionId, String prompt) {
        try {
            currentSessionContext.set(sessionId); // Bind session profile context metadata to execution thread scope
            Optional<SessionSummary> summary = sessionSummaryRepository.findTopBySessionIdOrderByIdDesc(sessionId);
            List<Message> history = messageRepository.findTop20ByChatSessionIdOrderByIdDesc(sessionId);
            Collections.reverse(history);

            if (!history.isEmpty()) {
                Message lastMessage = history.get(history.size() - 1);
                if ("user".equals(lastMessage.getRole()) && prompt.equals(lastMessage.getContent())) {
                    history.remove(history.size() - 1);
                }
            }

            List<org.springframework.ai.chat.messages.Message> systemMessages = new ArrayList<>();
            systemMessages.add(new SystemMessage(getSystemInstructionContext()));

            if (summary.isPresent() && summary.get().getSummary() != null && !summary.get().getSummary().isBlank()) {
                systemMessages.add(new SystemMessage("Historical conversation context summaries framework:\n" + summary.get().getSummary()));
            }

            for (Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    systemMessages.add(new UserMessage(msg.getContent()));
                } else {
                    systemMessages.add(new AssistantMessage(msg.getContent()));
                }
            }

            String retrievalContext = buildRetrievalContext(prompt);

            String finalPrompt =
                    retrievalContext.isBlank()
                            ? prompt
                            : retrievalContext
                            + "\n\nUser Question:\n"
                            + prompt;

            systemMessages.add(new UserMessage(finalPrompt));

            return chatClient.prompt()
                    .messages(systemMessages)
                    .toolNames("currentWeatherFunction", "currentTimeTool", "memorySearchTool", "searchDocumentTool", "executeCalculator", "searchWebTool")
                    .stream()
                    .content()
                    .timeout(Duration.ofSeconds(60))
                    .doFinally(signalType -> currentSessionContext.remove()); // Ensure background cleanups process natively

        } catch (Exception e) {
            currentSessionContext.remove();
            System.err.println("Streaming execution broke: " + e.getMessage());
            return Flux.just("❌ An internal server error occurred. Please try again.");
        }
    }

    private String getSystemInstructionContext() {
        return """
                You are an intelligent AI assistant.
                
                Answer directly using your own knowledge whenever possible.
                
                Use memorySearchTool only when the user is asking about:
                - themselves
                - personal preferences
                - remembered facts
                - previous conversations
                - information stored about the user
                
                Use searchDocumentTool only when the user is asking about:
                - uploaded files
                - PDFs
                - resumes
                - cover letters
                - attachments
                - document contents
                
                For general knowledge, programming, software engineering, mathematics, science, explanations, brainstorming, and normal conversation, answer directly without using tools.
                
                If a tool returns no useful information, continue answering normally instead of saying you cannot answer.
                
                Never claim you lack access to a document, memory, or file without first checking the appropriate tool.
                """;
    }

    public void indexDocumentIntoVectorStore(String textContent, String fileName, Long sessionId) {
        if (textContent == null || textContent.trim().isEmpty()) return;

        Document rawDocument = new Document(textContent, Map.of(
                "fileName", fileName,
                "sessionId", sessionId,
                "userEmail", getCurrentUserEmail(),
                "memoryType", "document_memory"
        ));

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(200)
                .withKeepSeparator(true)
                .build();
        List<Document> splitChunks = splitter.apply(List.of(rawDocument));

        this.vectorStore.accept(splitChunks);
        System.out.println("✅ Successfully indexed " + splitChunks.size() + " text chunks for Session ID: " + sessionId);
    }

    public String searchDocumentMemory(String query) {
        Long activeSessionId = currentSessionContext.get();
        if (activeSessionId == null) {
            return "No active session context available to search documents.";
        }

        // ✅ PRODUCTION ISOLATION FILTER: Isolates queries strictly to this session's attachments
//        org.springframework.ai.vectorstore.filter.Filter.Expression isolatedDocFilter =
//                new org.springframework.ai.vectorstore.filter.FilterExpressionBuilder()
//                        .expression("memoryType == 'document_memory' && sessionId == " + activeSessionId);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.35)
                .filterExpression("memoryType == 'document_memory' && sessionId == " + activeSessionId)
                .build();

        List<Document> docs = vectorStore.similaritySearch(request);
        if (docs == null || docs.isEmpty()) {
            return "No matching text entries found inside the session's uploaded documents.";
        }

        StringBuilder sb = new StringBuilder();
        for (Document doc : docs) {
            sb.append("[").append(doc.getMetadata().getOrDefault("fileName", "Document Chunk")).append("]:\n")
                    .append(doc.getText()).append("\n---\n");
        }
        return sb.toString();
    }

    private String getCurrentUserEmail() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getName();
        }
        return "anonymous_user";
    }

    public String searchGlobalMemory(String query) {
        Filter.Expression userFilter = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("userEmail"),
                new Filter.Value(getCurrentUserEmail())
        );

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(4)
                .similarityThreshold(0.55)
                .filterExpression(userFilter)
                .build();

        List<Document> docs = vectorStore.similaritySearch(request);
        if (docs == null || docs.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Document doc : docs) {
            String memoryType = doc.getMetadata().getOrDefault("memoryType", "assistant_memory").toString();
            sb.append("[").append(memoryType).append("]\n").append(doc.getText()).append("\n---\n");
        }
        return sb.toString();
    }

    public void indexChatMemory(Long sessionId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return;
        Document document = new Document(userMessage, Map.of(
                "userEmail", getCurrentUserEmail(),
                "sessionId", sessionId,
                "memoryType", "user_conversation_fact",
                "createdAt", System.currentTimeMillis()
        ));
        vectorStore.add(List.of(document));
    }

    public void indexAssistantMemory(Long sessionId, String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) return;
        Document document = new Document(aiResponse, Map.of(
                "userEmail", getCurrentUserEmail(),
                "sessionId", sessionId,
                "memoryType", "assistant_memory",
                "createdAt", System.currentTimeMillis()
        ));
        vectorStore.add(List.of(document));
    }

    public FileUploadResult handleFileUploadAndGetContent(MultipartFile file) {
        try {
            String mimeType = file.getContentType();
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", cloudinaryFolder, "resource_type", "auto"
            ));
            String cloudinaryUrl = (String) uploadResult.get("secure_url");

            if (mimeType != null && mimeType.startsWith("image/")) {
                return new FileUploadResult(cloudinaryUrl, mimeType, file.getOriginalFilename(), "", true);
            }

            String extractedText = tika.parseToString(file.getInputStream());
            return new FileUploadResult(cloudinaryUrl, mimeType, file.getOriginalFilename(), extractedText, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String generateMultimodalResponse(Long sessionId, UserMessage multimodalMessage) {
        try {
            List<Message> history = messageRepository.findTop20ByChatSessionIdOrderByIdDesc(sessionId);
            Collections.reverse(history);
            List<org.springframework.ai.chat.messages.Message> systemMessages = new ArrayList<>();

            for (Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    systemMessages.add(new UserMessage(msg.getContent()));
                } else {
                    systemMessages.add(new AssistantMessage(msg.getContent()));
                }
            }
            systemMessages.add(multimodalMessage);

            return chatClient.prompt().messages(systemMessages).call().content();
        } catch (Exception e) {
            return "Error analyzing image content details: " + e.getMessage();
        }
    }

    public void summarizeSession(Long sessionId) {
        List<Message> messages = messageRepository.findByChatSessionIdOrderByIdAsc(sessionId);
        if (messages.size() < 10) return;

        StringBuilder conversation = new StringBuilder();
        for (Message msg : messages) {
            conversation.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        try {
            String summary = chatClient.prompt()
                    .user("Summarize the important facts, user preferences, project details, goals and decisions.\n\n" + conversation)
                    .call()
                    .content();

            SessionSummary sessionSummary = new SessionSummary();
            sessionSummary.setSessionId(sessionId);
            sessionSummary.setSummary(summary);
            sessionSummaryRepository.save(sessionSummary);

            Document summaryDocument = new Document(summary, Map.of("sessionId", sessionId, "memoryType", "session_summary"));
            vectorStore.add(List.of(summaryDocument));
        } catch (Exception e) {
            System.err.println("⚠️ Session summary skipped: " + e.getMessage());
        }
    }
//    private String routeIntentAndEnrichPrompt(String rawPrompt) {
//        String cleanPrompt = rawPrompt.toLowerCase(Locale.ROOT);
//        StringBuilder contextEnrichment = new StringBuilder();
//
//        // 1. Hardcoded Route Path for Documents
//        if (cleanPrompt.contains("pdf")
//                || cleanPrompt.contains("document")
//                || cleanPrompt.contains("resume")
//                || cleanPrompt.contains("cover letter")
//                || cleanPrompt.contains("file")
//                || cleanPrompt.contains("attachment")) {
//
//            String docContext = searchDocumentMemory(rawPrompt);
//            if (docContext != null && !docContext.startsWith("No matching text entries")) {
//                contextEnrichment.append("\n=== AUTOMATIC ROUTER: RELEVANT UPLOADED DOCUMENTS ===\n")
//                        .append(docContext).append("\n");
//            }
//        }
//
//        // 2. Hardcoded Route Path for Personal Context Profile Elements
//        if (cleanPrompt.contains("remember")
//                || cleanPrompt.contains("preference")
//                || cleanPrompt.contains("my favorite")
//                || cleanPrompt.contains("about me")
//                || cleanPrompt.contains("past chat")
//                || cleanPrompt.contains("earlier conversation")) {
//
//            String profileContext = searchGlobalMemory(rawPrompt);
//            if (profileContext != null && !profileContext.isBlank() && !profileContext.startsWith("No matching long-term")) {
//                contextEnrichment.append("\n=== AUTOMATIC ROUTER: RELEVANT HISTORICAL MEMORIES ===\n")
//                        .append(profileContext).append("\n");
//            }
//        }
//
//        // If we gathered context from our router logic, wrap it cleanly around the user's question
//        if (!contextEnrichment.isEmpty()) {
//            return """
//               %s
//
//               [USER CURRENT INPUT QUESTION]:
//               %s
//               """.formatted(contextEnrichment.toString(), rawPrompt);
//        }
//
//        return rawPrompt; // Pass raw prompt along if no special keyword matches occurred
//    }
    private String buildRetrievalContext(String prompt) {
        System.out.println("Prompt = " + prompt);

        StringBuilder context = new StringBuilder();

        try {

            String documentContext = searchDocumentMemory(prompt);
            System.out.println("Document Context:");
            System.out.println(documentContext);

            if(documentContext != null
                    && !documentContext.isBlank()) {

                context.append("""
                    
                    === RELEVANT DOCUMENT INFORMATION ===
                    """);

                context.append(documentContext)
                        .append("\n");
            }

        } catch(Exception e) {
            System.out.println("Document retrieval skipped");
        }

        try {

            String memoryContext = searchGlobalMemory(prompt);
            System.out.println("Memory Context:");
            System.out.println(memoryContext);

            if(memoryContext != null
                    && !memoryContext.isBlank()) {

                context.append("""
                    
                    === RELEVANT USER MEMORY ===
                    """);

                context.append(memoryContext)
                        .append("\n");
            }

        } catch(Exception e) {
            System.out.println("Memory retrieval skipped");
        }

        return context.toString();
    }
}