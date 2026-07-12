package com.subhajit.aiassistant.Tools;

import com.subhajit.aiassistant.Configures.ToolRequest;
import com.subhajit.aiassistant.Services.AiService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class DocumentSearchTool {
    private final AiService aiService;

    public DocumentSearchTool(AiService aiService) {
        this.aiService = aiService;
    }

    @Bean
    @Description("""
        Use this tool whenever the user asks about or references:
        - Uploaded PDFs, documents, text files, or attachments
        - Resumes, CVs, portfolios, or cover letters
        - Specific content hidden within uploaded file resources
        - Prior file uploads from older chat workspaces
        Always invoke this tool to scan document memory before formulating an answer.
        """)
    public Function<ToolRequest, String> searchDocumentTool() {
        return request -> {
            System.out.println("TOOL -> DOCUMENT");
            String query = request.payload();
            System.out.println("QUERY = " + query);
            if (query == null || query.isBlank()) return "No document query phrase provided.";
            return aiService.searchDocumentMemory(query);
        };
    }
}