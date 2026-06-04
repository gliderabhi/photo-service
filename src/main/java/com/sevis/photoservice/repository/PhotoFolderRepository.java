package com.sevis.photoservice.repository;

import com.sevis.photoservice.model.PhotoFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PhotoFolderRepository extends JpaRepository<PhotoFolder, Long> {

    Optional<PhotoFolder> findByUserId(Long userId);
}
