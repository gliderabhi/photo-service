package com.sevis.photoservice.repository;

import com.sevis.photoservice.model.Face;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaceRepository extends JpaRepository<Face, Long> {

    List<Face> findByPhotoIdAndUserId(Long photoId, Long userId);

    List<Face> findByPersonIdAndUserId(Long personId, Long userId);

    void deleteByPhotoId(Long photoId);
}
