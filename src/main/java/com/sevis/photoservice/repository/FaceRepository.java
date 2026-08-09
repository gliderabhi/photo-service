package com.sevis.photoservice.repository;

import com.sevis.photoservice.model.Face;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaceRepository extends JpaRepository<Face, Long> {

    List<Face> findByPhotoIdAndUserId(Long photoId, Long userId);

    List<Face> findByPersonIdAndUserId(Long personId, Long userId);

    /** Every already-clustered face for this user, used to build the in-memory
     *  exemplar set FaceService matches new faces against (see assignPerson). */
    List<Face> findByUserIdAndPersonIdIsNotNull(Long userId);

    /** Every face row for this user regardless of clustering state — used to find
     *  which photos already have *some* detection result recorded, for backfillFaces(). */
    List<Face> findByUserId(Long userId);

    void deleteByPhotoId(Long photoId);
}
