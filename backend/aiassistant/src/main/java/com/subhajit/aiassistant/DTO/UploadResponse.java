package com.subhajit.aiassistant.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResponse {

    private String aiResponse;
    private String mediaUrl;
    private String mediaType;
    private String fileName;
    private String extractedContent;
}
