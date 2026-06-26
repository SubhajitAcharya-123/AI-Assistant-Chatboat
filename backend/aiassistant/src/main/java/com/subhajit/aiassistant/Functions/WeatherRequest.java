package com.subhajit.aiassistant.Functions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record WeatherRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The city and state/country, e.g. San Francisco, CA or Kolkata, India")
        String location
) {}