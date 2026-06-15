package com.sevis.photoservice.controller;

import com.sevis.photoservice.dto.request.BulkDeleteRequest;
import com.sevis.photoservice.dto.response.PhotoResponse;
import com.sevis.photoservice.dto.response.PhotosByDateResponse;
import com.sevis.photoservice.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoResponse> upload(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(photoService.upload(userId, folderPassword, file));
    }

    @GetMapping
    public ResponseEntity<List<PhotosByDateResponse>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword) {
        return ResponseEntity.ok(photoService.listGroupedByDate(userId, folderPassword));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> getContent(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @PathVariable Long id) {
        byte[] content = photoService.getPhotoContent(userId, id, folderPassword);
        String contentType = photoService.getContentType(userId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<Map<String, String>> bulkDelete(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @RequestBody BulkDeleteRequest request) {
        photoService.bulkDelete(userId, request.getPhotoIds(), folderPassword);
        return ResponseEntity.ok(Map.of("message", "Photos deleted"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @PathVariable Long id) {
        photoService.delete(userId, id, folderPassword);
        return ResponseEntity.ok(Map.of("message", "Photo deleted"));
    }
}
