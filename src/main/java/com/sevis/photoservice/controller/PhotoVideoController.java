package com.sevis.photoservice.controller;

import com.sevis.photoservice.dto.response.VideoResponse;
import com.sevis.photoservice.service.VideoProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Video upload/delete on behalf of the photos app. Listing, status polling and
 * HLS/thumbnail playback go straight from the mobile app to stream-service
 * through the gateway (see VideoResponse.masterPlaylistUrl/thumbnailUrl, which
 * are already gateway-relative "/stream-service/..." URLs) — proxying binary
 * streaming traffic through this service would add a needless extra hop.
 */
@RestController
@RequestMapping("/api/photos/videos")
@RequiredArgsConstructor
public class PhotoVideoController {

    private final VideoProxyService videoProxyService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoResponse> upload(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        return ResponseEntity.ok(videoProxyService.upload(userId, folderPassword, file, description));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @PathVariable UUID id) {
        videoProxyService.delete(userId, folderPassword, id);
        return ResponseEntity.ok(Map.of("message", "Video deleted"));
    }
}
