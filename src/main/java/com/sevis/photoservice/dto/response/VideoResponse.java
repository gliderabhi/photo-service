package com.sevis.photoservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors the subset of stream-service's VideoDto that the photos mobile app
 * needs. Extra fields on the stream-service response (seriesId, ownerUserId,
 * etc.) are ignored on deserialization.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoResponse {
    private UUID id;
    private String title;
    private String originalFilename;
    private String status;
    private int durationSeconds;
    private long fileSizeBytes;
    private int transcodeProgress;
    private String errorMessage;
    private String description;
    private String thumbnailUrl;
    private String masterPlaylistUrl;
    private List<String> availableQualities;
    private Instant createdAt;
}
