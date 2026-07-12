package com.subhajit.aiassistant.Tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;

@Configuration
public class TimeTool {

    @Bean
    @Description(
            "Returns the current date and time"
    )
    public Supplier<String> currentTimeTool() {
        System.out.println("TOOL EXECUTED -> Time Tool");
        return () -> LocalDateTime.now(ZoneId.of("Asia/Kolkata")).toString();
    }
}