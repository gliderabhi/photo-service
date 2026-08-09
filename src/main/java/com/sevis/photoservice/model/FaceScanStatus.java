package com.sevis.photoservice.model;

/**
 * Per-photo face-detection lifecycle (see Photo#faceScanStatus). Detection no longer runs
 * inline at upload time — every new photo starts PENDING and gets picked up later by
 * PhotoService#scanFaceBatch, called periodically by the client (see AutoUpload on both
 * platforms) rather than a true server-side cron: the server never stores folder
 * passwords, so it has nothing to decrypt photo bytes with on its own schedule — only a
 * request that actually carries X-Folder-Password can.
 */
public enum FaceScanStatus {
    /** Never attempted, or eligible for another attempt after previously failing. */
    PENDING,
    /** Attempted and completed — regardless of whether any faces were found. Never
     *  re-picked, so a genuine zero-face photo (landscape, receipt, screenshot, …)
     *  doesn't get rescanned forever. */
    DONE,
    /** Attempted and errored (e.g. an unreadable image, face-service unavailable).
     *  Still eligible for a retry up to Photo#faceScanAttempts' cap. */
    FAILED
}
