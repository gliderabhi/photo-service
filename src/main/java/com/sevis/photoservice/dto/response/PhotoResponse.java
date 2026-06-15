package com.sevis.photoservice.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PhotoResponse {
    private Long id;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private String url;
}
