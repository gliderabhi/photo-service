package com.sevis.photoservice.controller;

import com.sevis.photoservice.dto.request.RenamePersonRequest;
import com.sevis.photoservice.dto.response.FaceResponse;
import com.sevis.photoservice.dto.response.PersonResponse;
import com.sevis.photoservice.dto.response.PhotoResponse;
import com.sevis.photoservice.service.FaceService;
import com.sevis.photoservice.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    // Fire-and-forget: runs on photo-service's own background executor (see
    // PhotoService#backfillFaces) and can take a while for a large library, so this
    // returns immediately rather than making the caller wait for the whole scan.
    @PostMapping("/faces/backfill")
    public ResponseEntity<Map<String, String>> backfillFaces(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword) {
        photoService.backfillFaces(userId, folderPassword);
        return ResponseEntity.accepted().body(Map.of("message", "Face scan started in the background"));
    }
}
