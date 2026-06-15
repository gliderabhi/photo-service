package com.sevis.photoservice.service;

import com.sevis.photoservice.dto.response.PhotoResponse;
import com.sevis.photoservice.dto.response.PhotosByDateResponse;
import com.sevis.photoservice.model.Photo;
import com.sevis.photoservice.model.PhotoFolder;
import com.sevis.photoservice.repository.PhotoAlbumRepository;
import com.sevis.photoservice.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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

    public PhotoResponse upload(Long userId, String folderPassword, MultipartFile file) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);

        String originalName = file.getOriginalFilename();
        String extension = extractExtension(originalName);
        String storedName = UUID.randomUUID() + extension;

        Path targetPath = Path.of(folder.getFolderPath(), storedName);
        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file");
        }

        Photo photo = new Photo();
        photo.setUserId(userId);
        photo.setStoredFilename(storedName);
        photo.setOriginalFilename(originalName != null ? originalName : storedName);
        photo.setContentType(file.getContentType());
        photo.setFileSize(file.getSize());
        photoRepository.save(photo);

        return toResponse(photo);
    }

    public List<PhotosByDateResponse> listGroupedByDate(Long userId, String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);

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
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read photo");
        }
    }

    public String getContentType(Long userId, Long photoId) {
        Photo photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        return photo.getContentType() != null ? photo.getContentType() : "application/octet-stream";
    }

    @Transactional
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
        photoRepository.deleteByIdAndUserId(photoId, userId);
    }

    @Transactional
    public void bulkDelete(Long userId, List<Long> photoIds, String folderPassword) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);
        for (Long photoId : photoIds) {
            photoRepository.findByIdAndUserId(photoId, userId).ifPresent(photo -> {
                Path filePath = Path.of(folder.getFolderPath(), photo.getStoredFilename());
                try { Files.deleteIfExists(filePath); } catch (IOException ignored) {}
                photoAlbumRepository.deleteByPhotoId(photoId);
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
