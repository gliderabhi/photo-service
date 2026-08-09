package com.sevis.photoservice.repository;

import com.sevis.photoservice.model.FaceScanStatus;
import com.sevis.photoservice.model.Photo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<Photo> findByIdAndUserId(Long id, Long userId);

    Optional<Photo> findByUserIdAndContentHash(Long userId, String contentHash);

    void deleteByIdAndUserId(Long id, Long userId);

    // Used by PhotoService#scanFaceBatch — PENDING photos have no attempt cap
    // (every photo gets a first try), FAILED ones do (see the other query below).
    List<Photo> findByUserIdAndFaceScanStatusOrderByUploadedAtDesc(Long userId, FaceScanStatus status, Pageable pageable);

    List<Photo> findByUserIdAndFaceScanStatusAndFaceScanAttemptsLessThanOrderByUploadedAtDesc(
            Long userId, FaceScanStatus status, int maxAttempts, Pageable pageable);

    long countByUserIdAndFaceScanStatus(Long userId, FaceScanStatus status);
}
