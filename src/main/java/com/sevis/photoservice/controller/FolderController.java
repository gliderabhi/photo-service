package com.sevis.photoservice.controller;

import com.sevis.photoservice.dto.request.FolderSetupRequest;
import com.sevis.photoservice.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/photos/folder")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping("/setup")
    public ResponseEntity<Map<String, String>> setup(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody FolderSetupRequest request) {
        folderService.setupFolder(userId, request);
        return ResponseEntity.ok(Map.of("message", "Folder password configured successfully"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(Map.of("hasFolder", folderService.hasFolder(userId)));
    }

    /** Verifies the folder password without performing any other action — used by the UI to pre-check access. */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Folder-Password") String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        return ResponseEntity.ok(Map.of("message", "Password verified"));
    }
}
