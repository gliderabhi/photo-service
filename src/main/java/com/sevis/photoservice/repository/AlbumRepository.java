package com.sevis.photoservice.repository;

import com.sevis.photoservice.model.Album;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {
    List<Album> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Album> findByIdAndUserId(Long id, Long userId);
    void deleteByIdAndUserId(Long id, Long userId);
}
