package com.sevis.photoservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "photo_folders")
@Getter
@Setter
public class PhotoFolder {

    @Id
    private Long userId;

    /** BCrypt hash of the user-chosen folder password */
    @Column(nullable = false)
    private String passwordHash;

    /** Random salt used for PBKDF2 AES key derivation; hex-encoded, generated once at setup */
    @Column(nullable = false, length = 64)
    private String encryptionSalt;

    /** PBKDF2 iteration count used when this folder's key was first derived — never change after creation */
    @Column(nullable = false)
    private int pbkdf2Iterations = 120_000;

    /** Absolute path on disk where this user's photos are stored */
    @Column(nullable = false)
    private String folderPath;

    private LocalDateTime createdAt;
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
