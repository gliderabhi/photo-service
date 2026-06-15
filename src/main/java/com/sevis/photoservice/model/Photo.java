package com.sevis.photoservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "photos")
@Getter
@Setter
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** UUID-based name used for storage on disk */
    @Column(nullable = false, unique = true)
    private String storedFilename;

    /** Original filename as uploaded by the user */
    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String contentType;

    private Long fileSize;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
