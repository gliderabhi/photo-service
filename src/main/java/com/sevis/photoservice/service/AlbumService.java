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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
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

    // Self-injected proxy so the @Cacheable data-fetch methods below go
    // through Spring's caching interceptor even when called from other
    // methods in this same class (plain "this.foo()" self-invocation would
    // bypass the proxy and silently skip caching). @Lazy avoids a circular
    // construction dependency.
    @Autowired
    @Lazy
    private AlbumService self;

    @CacheEvict(value = "albumsByUser", key = "#userId")
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

    // Password verification (a security check) must run on every call and
    // must never itself be cached — only the pure data fetch below is.
    public List<AlbumResponse> listAlbums(Long userId, String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        return self.getAlbumsDataCached(userId);
    }

    @Cacheable(value = "albumsByUser", key = "#userId", sync = true)
    public List<AlbumResponse> getAlbumsDataCached(Long userId) {
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

    // Same split as listAlbums: auth check uncached, data fetch cached.
    public List<PhotoResponse> getAlbumPhotos(Long userId, Long albumId, String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        return self.getAlbumPhotosDataCached(userId, albumId);
    }

    @Cacheable(value = "albumPhotos", key = "#userId + ':' + #albumId", sync = true)
    public List<PhotoResponse> getAlbumPhotosDataCached(Long userId, Long albumId) {
        albumRepository.findByIdAndUserId(albumId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found"));
        return photoAlbumRepository.findByAlbumId(albumId).stream()
                .map(l -> photoRepository.findByIdAndUserId(l.getPhotoId(), userId).orElse(null))
                .filter(p -> p != null)
                .map(this::toPhotoResponse)
                .collect(Collectors.toList());
    }

    @Caching(evict = {
            @CacheEvict(value = "albumsByUser", key = "#userId"),
            @CacheEvict(value = "albumPhotos", key = "#userId + ':' + #albumId")
    })
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
    @Caching(evict = {
            @CacheEvict(value = "albumsByUser", key = "#userId"),
            @CacheEvict(value = "albumPhotos", key = "#userId + ':' + #albumId")
    })
    public void removePhoto(Long userId, Long albumId, Long photoId, String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        albumRepository.findByIdAndUserId(albumId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found"));
        photoAlbumRepository.deleteByAlbumIdAndPhotoId(albumId, photoId);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "albumsByUser", key = "#userId"),
            @CacheEvict(value = "albumPhotos", key = "#userId + ':' + #albumId")
    })
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
