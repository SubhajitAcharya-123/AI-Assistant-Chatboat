package com.subhajit.aiassistant.Tools;

import com.subhajit.aiassistant.Configures.ToolRequest;
import com.subhajit.aiassistant.Services.AiService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class MemoryTool {

    private final AiService aiService;

    public MemoryTool(AiService aiService) {
        this.aiService = aiService;
    }

    @Bean
    @Description("""
        Search the authenticated user's long-term profile memory bank.
        Use this tool whenever the user asks questions regarding:
        - User preferences, favorite coding languages, frameworks, or tech stacks
        - Personal identity facts, names, education status, grades, or personal details
        - Historical conversational facts discussed during earlier chats or old summaries
        Always invoke this tool to read memory banks before answering.
        """)
    public Function<ToolRequest, String> memorySearchTool() {
        return request -> {
            System.out.println("TOOL -> MEMORY");
            String query = request.payload();
            System.out.println("QUERY = " + query);
            if (query == null || query.isBlank()) return "No search terms provided.";
            String result = aiService.searchGlobalMemory(query);
            return (result.isBlank()) ? "No long-term memories match the search criteria." : result;
        };
    }
}