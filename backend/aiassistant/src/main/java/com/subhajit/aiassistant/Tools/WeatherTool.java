package com.subhajit.aiassistant.Tools;

import com.subhajit.aiassistant.Functions.WeatherRequest;
import com.subhajit.aiassistant.Functions.WeatherResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestClient;

import java.util.function.Function;

@Configuration
public class WeatherTool {

    @Bean
    @Description("Get the real-time weather details for a specific location using a live external API")
    public Function<WeatherRequest, WeatherResponse> currentWeatherFunction() {
        RestClient restClient = RestClient.create();
        return request -> {
            try {
                System.out.println("TOOL EXECUTED -> Weather Tool");

                String sanitizedLocation = request.location().replace(" ", "+");
                String realTimeReport =
                        restClient.get()
                                .uri(
                                        "https://wttr.in/"
                                                + sanitizedLocation
                                                + "?format=3"
                                )
                                .retrieve()
                                .body(String.class);

                return new WeatherResponse(
                        request.location(),
                        0.0,
                        realTimeReport
                );

            } catch (Exception e) {

                return new WeatherResponse(
                        request.location(),
                        0.0,
                        "Weather service unavailable."
                );
            }
        };
    }
}