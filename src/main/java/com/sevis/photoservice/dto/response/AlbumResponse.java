package com.sevis.photoservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AlbumResponse {
    private Long id;
    private String name;
    private int photoCount;
    private LocalDateTime createdAt;
    private PhotoResponse coverPhoto;
}
