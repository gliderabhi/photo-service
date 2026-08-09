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

    /** SHA-256 of the original (pre-encryption) file bytes, used to reject
     *  re-uploads of a file already in this user's folder. Nullable so
     *  existing rows predating this field don't need a backfill. */
    @Column(length = 64)
    private String contentHash;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    /** See FaceScanStatus — detection is decoupled from upload, so every new photo
     *  starts PENDING and PhotoService#scanFaceBatch picks it up later. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FaceScanStatus faceScanStatus = FaceScanStatus.PENDING;

    /** Truncated exception message from the last failed scan attempt — null once a
     *  scan succeeds. Purely diagnostic, not shown to the user. */
    @Column(length = 500)
    private String faceScanError;

    /** Capped in PhotoService#scanFaceBatch so a permanently-broken image (corrupt
     *  file, unsupported format) doesn't get retried forever. */
    @Column(nullable = false)
    private Integer faceScanAttempts = 0;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
