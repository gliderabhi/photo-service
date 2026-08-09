package com.sevis.photoservice.controller;

import com.sevis.photoservice.dto.request.BulkDeleteRequest;
import com.sevis.photoservice.dto.response.PhotoResponse;
import com.sevis.photoservice.dto.response.PhotosByDateResponse;
import com.sevis.photoservice.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    // maxDimension: grid thumbnails (mobile/web/TV galleries) request e.g. ?maxDimension=400
    // instead of downloading the full original just to shrink it into a ~100dp cell — the
    // dominant cost for a grid of these was always network transfer of the full file, not
    // server-side work, so this isn't cached server-side (see CacheConfig's "raw photo bytes
    // are never cached" policy — this app runs with a small heap, and it already recomputes
    // the un-scaled content on every request the same way).
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> getContent(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @PathVariable Long id,
            @RequestParam(required = false) Integer maxDimension) {
        byte[] content = maxDimension != null
                ? photoService.getPhotoThumbnail(userId, id, folderPassword, maxDimension)
                : photoService.getPhotoContent(userId, id, folderPassword);
        String contentType = maxDimension != null ? MediaType.IMAGE_JPEG_VALUE : photoService.getContentType(userId, id);
        // private: browser may cache, proxies must not — photo is user-specific and auth-gated
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
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
