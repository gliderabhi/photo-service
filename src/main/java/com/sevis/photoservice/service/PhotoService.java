package com.sevis.photoservice.service;

import com.sevis.photoservice.dto.response.FaceScanBatchResponse;
import com.sevis.photoservice.dto.response.PhotoResponse;
import com.sevis.photoservice.dto.response.PhotosByDateResponse;
import com.sevis.photoservice.model.FaceScanStatus;
import com.sevis.photoservice.model.Photo;
import com.sevis.photoservice.model.PhotoFolder;
import com.sevis.photoservice.repository.PhotoAlbumRepository;
import com.sevis.photoservice.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final PhotoAlbumRepository photoAlbumRepository;
    private final FolderService folderService;
    private final EncryptionService encryptionService;
    private final FaceService faceService;

    // Self-injected proxy so the @Cacheable data-fetch method below runs
    // through Spring's caching interceptor when invoked from listGroupedByDate
    // in this same class (plain self-invocation bypasses the proxy). @Lazy
    // avoids a circular construction dependency.
    @Autowired
    @Lazy
    private PhotoService self;

    // A fresh upload doesn't touch album listings/covers directly (that only
    // happens via AlbumService.addPhotos), so only the date-grouped listing
    // needs invalidating here.
    @CacheEvict(value = "photosByDate", key = "#userId")
    public PhotoResponse upload(Long userId, String folderPassword, MultipartFile file) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read upload");
        }

        // Hash the plaintext bytes, not the on-disk ciphertext — encryption uses
        // a fresh IV/salt per write, so identical plaintext never produces the
        // same ciphertext to compare against.
        String hash = sha256Hex(rawBytes);
        var existing = photoRepository.findByUserIdAndContentHash(userId, hash);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        String originalName = file.getOriginalFilename();
        String extension = extractExtension(originalName);
        String storedName = UUID.randomUUID() + extension;

        Path targetPath = Path.of(folder.getFolderPath(), storedName);
        try {
            Files.createDirectories(targetPath.getParent());
            byte[] encrypted = encryptionService.encrypt(rawBytes, folderPassword, folder.getEncryptionSalt(), folder.getPbkdf2Iterations());
            Files.write(targetPath, encrypted);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file");
        }

        Photo photo = new Photo();
        photo.setUserId(userId);
        photo.setStoredFilename(storedName);
        photo.setOriginalFilename(originalName != null ? originalName : storedName);
        photo.setContentType(file.getContentType());
        photo.setFileSize(file.getSize());
        photo.setContentHash(hash);
        // faceScanStatus defaults to PENDING — detection is intentionally *not*
        // triggered here anymore (see scanFaceBatch() below). It used to run
        // fire-and-forget right at this point, against the plaintext bytes still in
        // hand — the only point they exist outside the encrypted-at-rest file — but
        // that coupled upload's request path to face-service's availability/latency
        // for no real benefit, and made every upload path (this one, auto-upload,
        // any future one) responsible for remembering to call it. Decoupled instead:
        // the client calls scanFaceBatch() periodically (see both platforms'
        // AutoUpload), which re-decrypts a small batch of PENDING/FAILED photos itself.
        photoRepository.save(photo);

        return toResponse(photo);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // Password verification (a security check) must run on every call and
    // must never itself be cached — only the pure data fetch below is.
    public List<PhotosByDateResponse> listGroupedByDate(Long userId, String folderPassword) {
        folderService.verifyAndGetFolder(userId, folderPassword);
        return self.getPhotosByDateCached(userId);
    }

    @Cacheable(value = "photosByDate", key = "#userId", sync = true)
    public List<PhotosByDateResponse> getPhotosByDateCached(Long userId) {
        List<Photo> photos = photoRepository.findByUserIdOrderByUploadedAtDesc(userId);

        Map<LocalDate, List<Photo>> grouped = photos.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getUploadedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        // Return in reverse-date order (newest first)
        return grouped.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<Photo>>comparingByKey().reversed())
                .map(entry -> PhotosByDateResponse.builder()
                        .date(entry.getKey())
                        .photos(entry.getValue().stream().map(this::toResponse).collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    public byte[] getPhotoContent(Long userId, Long photoId, String folderPassword) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);

        Photo photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));

        Path filePath = Path.of(folder.getFolderPath(), photo.getStoredFilename());
        try {
            byte[] encrypted = Files.readAllBytes(filePath);
            return encryptionService.decrypt(encrypted, folderPassword, folder.getEncryptionSalt(), folder.getPbkdf2Iterations());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read photo");
        }
    }

    // Not cached (see CacheConfig) — recomputed on every request the same way
    // getPhotoContent already is, just on a much smaller output. Falls back to the
    // full-size original if the source can't be decoded as a raster image (e.g. a
    // HEIC upload — the JDK's bundled ImageIO has no HEIC reader without an extra
    // native plugin dependency this service doesn't carry), so a thumbnail request
    // never hard-fails, just loses the size win for that one format.
    public byte[] getPhotoThumbnail(Long userId, Long photoId, String folderPassword, int maxDimension) {
        byte[] full = getPhotoContent(userId, photoId, folderPassword);
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(full));
            if (source == null) return full;

            int width = source.getWidth();
            int height = source.getHeight();
            double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
            if (scale >= 1.0) return full; // already smaller than the requested thumbnail
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));

            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            g.dispose();
            source.flush();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", out);
            resized.flush();
            return out.toByteArray();
        } catch (IOException e) {
            return full;
        }
    }

    // Retry cap for FAILED photos in scanFaceBatch() — PENDING photos (first attempt
    // ever) have no cap, only repeat attempts after a failure do.
    private static final int MAX_FACE_SCAN_ATTEMPTS = 3;

    /**
     * Scans up to [limit] of this user's not-yet-scanned (or previously failed, under
     * the attempt cap) photos, synchronously within this call — small, bounded
     * batches instead of the old fire-and-forget whole-library backfill, so it's
     * cheap to call repeatedly from the client's own periodic sync cycle (see both
     * platforms' AutoUpload) without ever blocking upload() itself, which no longer
     * triggers detection at all (see upload() above). Each photo's faceScanStatus is
     * persisted as it's processed (see FaceService#detectAndStoreSync), so a photo
     * already scanned — successfully or not, up to the attempt cap — is never
     * re-picked by a later call.
     */
    public FaceScanBatchResponse scanFaceBatch(Long userId, String folderPassword, int limit) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);

        List<Photo> batch = new java.util.ArrayList<>(
                photoRepository.findByUserIdAndFaceScanStatusOrderByUploadedAtDesc(
                        userId, FaceScanStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, limit)));
        if (batch.size() < limit) {
            batch.addAll(photoRepository.findByUserIdAndFaceScanStatusAndFaceScanAttemptsLessThanOrderByUploadedAtDesc(
                    userId, FaceScanStatus.FAILED, MAX_FACE_SCAN_ATTEMPTS,
                    org.springframework.data.domain.PageRequest.of(0, limit - batch.size())));
        }

        int scanned = 0;
        for (Photo photo : batch) {
            try {
                Path filePath = Path.of(folder.getFolderPath(), photo.getStoredFilename());
                byte[] encrypted = Files.readAllBytes(filePath);
                byte[] plain = encryptionService.decrypt(encrypted, folderPassword, folder.getEncryptionSalt(), folder.getPbkdf2Iterations());
                faceService.detectAndStoreSync(photo, plain);
                scanned++;
            } catch (Exception e) {
                photo.setFaceScanStatus(FaceScanStatus.FAILED);
                photo.setFaceScanError(e.getMessage());
                photo.setFaceScanAttempts(photo.getFaceScanAttempts() + 1);
                photoRepository.save(photo);
                log.warn("Face scan failed for photo {} (couldn't read/decrypt): {}", photo.getId(), e.getMessage());
            }
        }

        long remaining = photoRepository.countByUserIdAndFaceScanStatus(userId, FaceScanStatus.PENDING)
                + photoRepository.countByUserIdAndFaceScanStatus(userId, FaceScanStatus.FAILED);
        log.info("Face scan batch for user {}: scanned {}, {} remaining", userId, scanned, remaining);
        return FaceScanBatchResponse.builder().scanned(scanned).remaining(remaining).build();
    }

    public String getContentType(Long userId, Long photoId) {
        Photo photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        return photo.getContentType() != null ? photo.getContentType() : "application/octet-stream";
    }

    // Deleting a photo may remove it from any number of albums
    // (photoAlbumRepository.deleteByPhotoId below) and we don't know which
    // albums were affected here, so albumPhotos is evicted wholesale rather
    // than trying to compute the precise album keys — correctness over hit-rate.
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "photosByDate", key = "#userId"),
            @CacheEvict(value = "albumsByUser", key = "#userId"),
            @CacheEvict(value = "albumPhotos", allEntries = true)
    })
    public void delete(Long userId, Long photoId, String folderPassword) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);

        Photo photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));

        Path filePath = Path.of(folder.getFolderPath(), photo.getStoredFilename());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete file");
        }
        photoAlbumRepository.deleteByPhotoId(photoId);
        faceService.deleteFacesForPhoto(photoId);
        photoRepository.deleteByIdAndUserId(photoId, userId);
    }

    // Same reasoning as delete() above — any of the deleted photos could have
    // belonged to any album, so albumPhotos is evicted wholesale.
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "photosByDate", key = "#userId"),
            @CacheEvict(value = "albumsByUser", key = "#userId"),
            @CacheEvict(value = "albumPhotos", allEntries = true)
    })
    public void bulkDelete(Long userId, List<Long> photoIds, String folderPassword) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);
        for (Long photoId : photoIds) {
            photoRepository.findByIdAndUserId(photoId, userId).ifPresent(photo -> {
                Path filePath = Path.of(folder.getFolderPath(), photo.getStoredFilename());
                try { Files.deleteIfExists(filePath); } catch (IOException ignored) {}
                photoAlbumRepository.deleteByPhotoId(photoId);
                faceService.deleteFacesForPhoto(photoId);
                photoRepository.deleteByIdAndUserId(photoId, userId);
            });
        }
    }

    private PhotoResponse toResponse(Photo photo) {
        return PhotoResponse.builder()
                .id(photo.getId())
                .originalFilename(photo.getOriginalFilename())
                .contentType(photo.getContentType())
                .fileSize(photo.getFileSize())
                .uploadedAt(photo.getUploadedAt())
                .url("/api/photos/" + photo.getId() + "/content")
                .build();
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
