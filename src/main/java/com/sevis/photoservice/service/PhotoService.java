package com.sevis.photoservice.service;

import com.sevis.photoservice.dto.response.PhotoResponse;
import com.sevis.photoservice.dto.response.PhotosByDateResponse;
import com.sevis.photoservice.model.Photo;
import com.sevis.photoservice.model.PhotoFolder;
import com.sevis.photoservice.repository.PhotoAlbumRepository;
import com.sevis.photoservice.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final PhotoAlbumRepository photoAlbumRepository;
    private final FolderService folderService;
    private final EncryptionService encryptionService;
    private final FaceService faceService;

    // Self-injected proxy so the @Cacheable data-fetch method below runs
    // through Spring's caching interceptor when invoked from listGroupedByDate
    // in this same class (plain self-invocation bypasses the proxy). @Lazy
    // avoids a circular construction dependency.
    @Autowired
    @Lazy
    private PhotoService self;

    // A fresh upload doesn't touch album listings/covers directly (that only
    // happens via AlbumService.addPhotos), so only the date-grouped listing
    // needs invalidating here.
    @CacheEvict(value = "photosByDate", key = "#userId")
    public PhotoResponse upload(Long userId, String folderPassword, MultipartFile file) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read upload");
        }

        // Hash the plaintext bytes, not the on-disk ciphertext — encryption uses
        // a fresh IV/salt per write, so identical plaintext never produces the
        // same ciphertext to compare against.
        String hash = sha256Hex(rawBytes);
        var existing = photoRepository.findByUserIdAndContentHash(userId, hash);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        String originalName = file.getOriginalFilename();
        String extension = extractExtension(originalName);
        String storedName = UUID.randomUUID() + extension;

        Path targetPath = Path.of(folder.getFolderPath(), storedName);
        try {
            Files.createDirectories(targetPath.getParent());
            byte[] encrypted = encryptionService.encrypt(rawBytes, folderPassword, folder.getEncryptionSalt(), folder.getPbkdf2Iterations());
            Files.write(targetPath, encrypted);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file");
        }

        Photo photo = new Photo();
        photo.setUserId(userId);
        photo.setStoredFilename(storedName);
        photo.setOriginalFilename(originalName != null ? originalName : storedName);
        photo.setContentType(file.getContentType());
        photo.setFileSize(file.getSize());
        photo.setContentHash(hash);
        photoRepository.save(photo);

        // Fire-and-forget: face detection runs on face-service against the
        // plaintext bytes still in hand here — this is the only point they
        // exist outside the encrypted-at-rest file, since they're about to go
        // out of scope. Runs async so a slow/unavailable face-service never
        // delays the upload response.
        faceService.detectAndStoreAsync(userId, photo.getId(), rawBytes, originalName);

        return toResponse(photo);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // Password verification (a security check) must run on every call and
    // must never itself be cached — only the pure data fetch below is.
    public List<PhotosByDateResponse> listGroupedByDate(Long userId, String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        return self.getPhotosByDateCached(userId);
    }

    @Cacheable(value = "photosByDate", key = "#userId", sync = true)
    public List<PhotosByDateResponse> getPhotosByDateCached(Long userId) {
        List<Photo> photos = photoRepository.findByUserIdOrderByUploadedAtDesc(userId);

        Map<LocalDate, List<Photo>> grouped = photos.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getUploadedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        // Return in reverse-date order (newest first)
        return grouped.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<Photo>>comparingByKey().reversed())
                .map(entry -> PhotosByDateResponse.builder()
                        .date(entry.getKey())
                        .photos(entry.getValue().stream().map(this::toResponse).collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    public byte[] getPhotoContent(Long userId, Long photoId, String folderPassword) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);

        Photo photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));

        Path filePath = Path.of(folder.getFolderPath(), photo.getStoredFilename());
        try {
            byte[] encrypted = Files.readAllBytes(filePath);
            return encryptionService.decrypt(encrypted, folderPassword, folder.getEncryptionSalt(), folder.getPbkdf2Iterations());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read photo");
        }
    }

    public String getContentType(Long userId, Long photoId) {
        Photo photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        return photo.getContentType() != null ? photo.getContentType() : "application/octet-stream";
    }

    // Deleting a photo may remove it from any number of albums
    // (photoAlbumRepository.deleteByPhotoId below) and we don't know which
    // albums were affected here, so albumPhotos is evicted wholesale rather
    // than trying to compute the precise album keys — correctness over hit-rate.
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "photosByDate", key = "#userId"),
            @CacheEvict(value = "albumsByUser", key = "#userId"),
            @CacheEvict(value = "albumPhotos", allEntries = true)
    })
    public void delete(Long userId, Long photoId, String folderPassword) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);

        Photo photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));

        Path filePath = Path.of(folder.getFolderPath(), photo.getStoredFilename());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete file");
        }
        photoAlbumRepository.deleteByPhotoId(photoId);
        faceService.deleteFacesForPhoto(photoId);
        photoRepository.deleteByIdAndUserId(photoId, userId);
    }

    // Same reasoning as delete() above — any of the deleted photos could have
    // belonged to any album, so albumPhotos is evicted wholesale.
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "photosByDate", key = "#userId"),
            @CacheEvict(value = "albumsByUser", key = "#userId"),
            @CacheEvict(value = "albumPhotos", allEntries = true)
    })
    public void bulkDelete(Long userId, List<Long> photoIds, String folderPassword) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);
        for (Long photoId : photoIds) {
            photoRepository.findByIdAndUserId(photoId, userId).ifPresent(photo -> {
                Path filePath = Path.of(folder.getFolderPath(), photo.getStoredFilename());
                try { Files.deleteIfExists(filePath); } catch (IOException ignored) {}
                photoAlbumRepository.deleteByPhotoId(photoId);
                faceService.deleteFacesForPhoto(photoId);
                photoRepository.deleteByIdAndUserId(photoId, userId);
            });
        }
    }

    private PhotoResponse toResponse(Photo photo) {
        return PhotoResponse.builder()
                .id(photo.getId())
                .originalFilename(photo.getOriginalFilename())
                .contentType(photo.getContentType())
                .fileSize(photo.getFileSize())
                .uploadedAt(photo.getUploadedAt())
                .url("/api/photos/" + photo.getId() + "/content")
                .build();
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
