package com.subhajit.aiassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import java.util.function.Function;
import com.subhajit.aiassistant.Functions.WeatherRequest;
import com.subhajit.aiassistant.Functions.WeatherResponse;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class AiassistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiassistantApplication.class, args);
	}
    @Bean
    @Description("Get the real-time weather details for a specific location using a live external API")
    public Function<WeatherRequest, WeatherResponse> currentWeatherFunction() {
        // Initialize a lightweight Spring RestClient instance
        RestClient restClient = RestClient.create();

        return request -> {
            try {
                // Clean the location query parameter string
                String sanitizedLocation = request.location().replace(" ", "+");

                // We request format (?format=3) which returns a clean plain text report like: "Kolkata: 🌦️ +32°C 💨15km/h"
                String realTimeReport = restClient.get()
                        .uri("https://wttr.in/" + sanitizedLocation + "?format=3")
                        .retrieve()
                        .body(String.class);

                System.out.println("🌐 Live API Tool Executed for location [" + request.location() + "]: " + realTimeReport);

                return new WeatherResponse(request.location(),0.0, realTimeReport);

            } catch (Exception e) {
                System.err.println("❌ Failed to call external weather API: " + e.getMessage());
                return new WeatherResponse(request.location(),0.0, "Error: Could not reach live weather service provider right now.");
            }
        };
    }
}


