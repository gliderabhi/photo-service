package com.sevis.photoservice.repository;

import com.sevis.photoservice.model.PhotoAlbum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PhotoAlbumRepository extends JpaRepository<PhotoAlbum, Long> {
    List<PhotoAlbum> findByAlbumId(Long albumId);
    boolean existsByAlbumIdAndPhotoId(Long albumId, Long photoId);
    @Transactional
    void deleteByAlbumIdAndPhotoId(Long albumId, Long photoId);
    @Transactional
    void deleteByPhotoId(Long photoId);
    @Transactional
    void deleteByAlbumId(Long albumId);
}
