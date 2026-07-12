package com.subhajit.aiassistant.Tools;

import com.subhajit.aiassistant.Configures.ToolRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestClient;

import java.util.function.Function;

@Configuration
public class WebSearchTool {
    private final RestClient restClient = RestClient.create();
    @Bean
    @Description("Search the internet for current information")
    public Function<ToolRequest, String> searchWebTool() {
        System.out.println("TOOL -> WEBSEARCH");
        return request -> "Web search result for: " + request.payload();
    }
}
