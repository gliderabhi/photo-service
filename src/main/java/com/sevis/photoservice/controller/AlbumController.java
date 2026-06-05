package com.sevis.photoservice.controller;

import com.sevis.photoservice.dto.request.AddPhotosToAlbumRequest;
import com.sevis.photoservice.dto.request.CreateAlbumRequest;
import com.sevis.photoservice.dto.response.AlbumResponse;
import com.sevis.photoservice.dto.response.PhotoResponse;
import com.sevis.photoservice.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    @PostMapping
    public ResponseEntity<AlbumResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @RequestBody CreateAlbumRequest request) {
        return ResponseEntity.ok(albumService.createAlbum(userId, folderPassword, request));
    }

    @GetMapping
    public ResponseEntity<List<AlbumResponse>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword) {
        return ResponseEntity.ok(albumService.listAlbums(userId, folderPassword));
    }

    @GetMapping("/{id}/photos")
    public ResponseEntity<List<PhotoResponse>> getPhotos(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @PathVariable Long id) {
        return ResponseEntity.ok(albumService.getAlbumPhotos(userId, id, folderPassword));
    }

    @PostMapping("/{id}/photos")
    public ResponseEntity<Map<String, String>> addPhotos(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @PathVariable Long id,
            @RequestBody AddPhotosToAlbumRequest request) {
        albumService.addPhotos(userId, id, folderPassword, request.getPhotoIds());
        return ResponseEntity.ok(Map.of("message", "Photos added to album"));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Map<String, String>> removePhoto(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @PathVariable Long id,
            @PathVariable Long photoId) {
        albumService.removePhoto(userId, id, photoId, folderPassword);
        return ResponseEntity.ok(Map.of("message", "Photo removed from album"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteAlbum(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @PathVariable Long id) {
        albumService.deleteAlbum(userId, id, folderPassword);
        return ResponseEntity.ok(Map.of("message", "Album deleted"));
    }
}
