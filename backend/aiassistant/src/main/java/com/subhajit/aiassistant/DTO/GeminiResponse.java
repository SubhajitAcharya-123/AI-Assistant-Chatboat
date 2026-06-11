package com.subhajit.aiassistant.DTO;

import java.util.List;

public record GeminiResponse(List<Candidate> candidates) {
}
