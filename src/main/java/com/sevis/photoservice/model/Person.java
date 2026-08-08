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

    // No stored centroid: matching a new face against a person compares it
    // against every one of that person's own stored Face embeddings (capped —
    // see FaceService.MAX_EXEMPLARS_PER_PERSON), not a single blended-average
    // vector. A running-average centroid drifts as more angles/lighting get
    // folded in, which was causing the same person to intermittently miss
    // its own match and get split into a new Person — exemplar matching
    // doesn't have that failure mode.
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
