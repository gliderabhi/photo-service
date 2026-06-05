package com.sevis.photoservice.service;

import com.sevis.photoservice.dto.request.CreateAlbumRequest;
import com.sevis.photoservice.dto.response.AlbumResponse;
import com.sevis.photoservice.dto.response.PhotoResponse;
import com.sevis.photoservice.model.Album;
import com.sevis.photoservice.model.Photo;
import com.sevis.photoservice.model.PhotoAlbum;
import com.sevis.photoservice.repository.AlbumRepository;
import com.sevis.photoservice.repository.PhotoAlbumRepository;
import com.sevis.photoservice.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final PhotoAlbumRepository photoAlbumRepository;
    private final PhotoRepository photoRepository;
    private final FolderService folderService;

    public AlbumResponse createAlbum(Long userId, String folderPassword, CreateAlbumRequest request) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album name must not be blank");
        }
        Album album = new Album();
        album.setUserId(userId);
        album.setName(request.getName().trim());
        albumRepository.save(album);
        return toResponse(album, List.of());
    }

    public List<AlbumResponse> listAlbums(Long userId, String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        return albumRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(album -> {
                    List<PhotoAlbum> links = photoAlbumRepository.findByAlbumId(album.getId());
                    List<Photo> photos = links.stream()
                            .map(l -> photoRepository.findByIdAndUserId(l.getPhotoId(), userId).orElse(null))
                            .filter(p -> p != null)
                            .collect(Collectors.toList());
                    PhotoResponse cover = photos.isEmpty() ? null : toPhotoResponse(photos.get(0));
                    return toResponse(album, photos, cover);
                })
                .collect(Collectors.toList());
    }

    public List<PhotoResponse> getAlbumPhotos(Long userId, Long albumId, String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        albumRepository.findByIdAndUserId(albumId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found"));
        return photoAlbumRepository.findByAlbumId(albumId).stream()
                .map(l -> photoRepository.findByIdAndUserId(l.getPhotoId(), userId).orElse(null))
                .filter(p -> p != null)
                .map(this::toPhotoResponse)
                .collect(Collectors.toList());
    }

    public void addPhotos(Long userId, Long albumId, String folderPassword, List<Long> photoIds) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        albumRepository.findByIdAndUserId(albumId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found"));
        for (Long photoId : photoIds) {
            photoRepository.findByIdAndUserId(photoId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo " + photoId + " not found"));
            if (!photoAlbumRepository.existsByAlbumIdAndPhotoId(albumId, photoId)) {
                PhotoAlbum link = new PhotoAlbum();
                link.setAlbumId(albumId);
                link.setPhotoId(photoId);
                photoAlbumRepository.save(link);
            }
        }
    }

    @Transactional
    public void removePhoto(Long userId, Long albumId, Long photoId, String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        albumRepository.findByIdAndUserId(albumId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found"));
        photoAlbumRepository.deleteByAlbumIdAndPhotoId(albumId, photoId);
    }

    @Transactional
    public void deleteAlbum(Long userId, Long albumId, String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        albumRepository.findByIdAndUserId(albumId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found"));
        photoAlbumRepository.deleteByAlbumId(albumId);
        albumRepository.deleteByIdAndUserId(albumId, userId);
    }

    private AlbumResponse toResponse(Album album, List<Photo> photos) {
        return toResponse(album, photos, null);
    }

    private AlbumResponse toResponse(Album album, List<Photo> photos, PhotoResponse cover) {
        return AlbumResponse.builder()
                .id(album.getId())
                .name(album.getName())
                .photoCount(photos.size())
                .createdAt(album.getCreatedAt())
                .coverPhoto(cover)
                .build();
    }

    private PhotoResponse toPhotoResponse(Photo photo) {
        return PhotoResponse.builder()
                .id(photo.getId())
                .originalFilename(photo.getOriginalFilename())
                .contentType(photo.getContentType())
                .fileSize(photo.getFileSize())
                .uploadedAt(photo.getUploadedAt())
                .url("/api/photos/" + photo.getId() + "/content")
                .build();
    }
}
