package com.sevis.photoservice.service;

import com.sevis.photoservice.dto.request.FolderSetupRequest;
import com.sevis.photoservice.model.PhotoFolder;
import com.sevis.photoservice.repository.PhotoFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final PhotoFolderRepository folderRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${photo.storage.base-dir}")
    private String baseDir;

    public void setupFolder(Long userId, FolderSetupRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not be blank");
        }

        PhotoFolder folder = folderRepository.findByUserId(userId).orElse(null);

        if (folder != null) {
            // Changing an existing password requires verifying the current one
            if (request.getCurrentPassword() == null ||
                    !passwordEncoder.matches(request.getCurrentPassword(), folder.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
            }
            folder.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            folderRepository.save(folder);
        } else {
            String folderPath = resolveFolderPath(userId);
            createFolderOnDisk(folderPath);

            PhotoFolder newFolder = new PhotoFolder();
            newFolder.setUserId(userId);
            newFolder.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            newFolder.setFolderPath(folderPath);
            folderRepository.save(newFolder);
        }
    }

    public boolean hasFolder(Long userId) {
        return folderRepository.findByUserId(userId).isPresent();
    }

    /** Verifies the folder password and returns the folder entity if valid. */
    public PhotoFolder verifyAndGetFolder(Long userId, String password) {
        PhotoFolder folder = folderRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Folder not set up. Please set a folder password first."));

        if (!passwordEncoder.matches(password, folder.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect folder password");
        }
        return folder;
    }

    public String resolveFolderPath(Long userId) {
        return Paths.get(baseDir, String.valueOf(userId)).toAbsolutePath().toString();
    }

    private void createFolderOnDisk(String folderPath) {
        try {
            Files.createDirectories(Path.of(folderPath));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create storage folder: " + e.getMessage());
        }
    }
}
