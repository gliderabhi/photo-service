package com.sevis.photoservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One detected face within a Photo, produced by face-service at upload time
 * from the plaintext image bytes (before they're encrypted to disk — see
 * PhotoService.upload). Box coordinates are stored as fractions (0..1) of the
 * image's width/height so they stay valid regardless of what resolution a
 * client renders the photo at.
 */
@Entity
@Table(name = "faces")
@Getter
@Setter
public class Face {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long photoId;

    /** Null until clustered — see FaceService.assignPerson. */
    private Long personId;

    @Column(nullable = false)
    private Double boxTop;

    @Column(nullable = false)
    private Double boxRight;

    @Column(nullable = false)
    private Double boxBottom;

    @Column(nullable = false)
    private Double boxLeft;

    /** face_recognition's 128-d embedding, comma-separated doubles. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String embedding;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
