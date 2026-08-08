package com.sevis.photoservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A cluster of faces the server believes belong to the same individual,
 * built incrementally as photos are uploaded (see FaceService). Unnamed
 * until the user labels it via PATCH /api/photos/people/{id}.
 */
@Entity
@Table(name = "people")
@Getter
@Setter
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Null until the user names this person. */
    private String label;

    /** Running-average embedding of every Face assigned to this person, comma-separated doubles. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String centroidEmbedding;

    @Column(nullable = false)
    private Integer faceCount;

    /** First face assigned to this person — used as the representative thumbnail. */
    private Long coverFaceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
