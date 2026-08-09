package com.sevis.photoservice.controller;

import com.sevis.photoservice.dto.request.RenamePersonRequest;
import com.sevis.photoservice.dto.response.FaceResponse;
import com.sevis.photoservice.dto.response.FaceScanBatchResponse;
import com.sevis.photoservice.dto.response.PersonResponse;
import com.sevis.photoservice.dto.response.PhotoResponse;
import com.sevis.photoservice.service.FaceService;
import com.sevis.photoservice.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read side of server-side face detection (see FaceService for the write
 * side, triggered from PhotoService.upload). No folder password required
 * here — these return only face geometry and person metadata, never photo
 * bytes, so they don't need to decrypt anything (unlike /api/photos/{id}/content).
 */
@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class FaceController {

    private final FaceService faceService;
    private final PhotoService photoService;

    @GetMapping("/{photoId}/faces")
    public ResponseEntity<List<FaceResponse>> facesForPhoto(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long photoId) {
        return ResponseEntity.ok(faceService.facesForPhoto(userId, photoId));
    }

    @GetMapping("/people")
    public ResponseEntity<List<PersonResponse>> listPeople(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(faceService.listPeople(userId));
    }

    @GetMapping("/people/{personId}/photos")
    public ResponseEntity<List<PhotoResponse>> photosForPerson(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long personId) {
        return ResponseEntity.ok(faceService.photosForPerson(userId, personId));
    }

    @PatchMapping("/people/{personId}")
    public ResponseEntity<PersonResponse> renamePerson(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long personId,
            @RequestBody RenamePersonRequest request) {
        return ResponseEntity.ok(faceService.renamePerson(userId, personId, request));
    }

    // Synchronous, bounded batch — see PhotoService#scanFaceBatch. Meant to be
    // called repeatedly (the client's periodic auto-upload cycle on both platforms,
    // or SettingsScreen's manual "Scan now") rather than once for an entire library;
    // [remaining] tells the caller whether another call would still find work to do.
    @PostMapping("/faces/scan")
    public ResponseEntity<FaceScanBatchResponse> scanFaces(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(photoService.scanFaceBatch(userId, folderPassword, limit));
    }
}
