package com.sevis.photoservice.service;

import com.sevis.photoservice.dto.response.VideoResponse;
import com.sevis.photoservice.model.PhotoFolder;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Uploads/deletes videos on behalf of the photos app by delegating to
 * stream-service's existing ffmpeg splitting/encoding pipeline (reused as-is —
 * see project decision to not duplicate the transcoding pipeline). This service's
 * only job is: verify the folder password, decide *where* stream-service should
 * store the video (storageBaseDir, under this user's own photo folder), and
 * forward the request with a trusted X-User-Id / sourceApp=PHOTOS tag.
 */
@Service
@RequiredArgsConstructor
public class VideoProxyService {

    private static final String STREAM_SERVICE_VIDEOS_URL = "http://stream-service/api/videos";

    private final FolderService folderService;
    private final RestTemplate restTemplate;

    public VideoResponse upload(Long userId, String folderPassword, MultipartFile file, String description) {
        PhotoFolder folder = folderService.verifyAndGetFolder(userId, folderPassword);

        String storageBaseDir = Paths.get(folder.getFolderPath(), "videos", UUID.randomUUID().toString())
                .toAbsolutePath().toString();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", toResource(file));
        if (description != null && !description.isBlank()) body.add("description", description);
        body.add("sourceApp", "PHOTOS");
        body.add("ownerUserId", userId);
        body.add("storageBaseDir", storageBaseDir);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-User-Id", String.valueOf(userId));

        try {
            ResponseEntity<VideoResponse> response = restTemplate.postForEntity(
                    STREAM_SERVICE_VIDEOS_URL + "/upload", new HttpEntity<>(body, headers), VideoResponse.class);
            return response.getBody();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new ResponseStatusException(HttpStatus.valueOf(e.getStatusCode().value()),
                    "stream-service rejected the upload: " + e.getStatusText());
        }
    }

    public void delete(Long userId, String folderPassword, UUID videoId) {
        // Folder password gates deletion the same way it gates photo deletion —
        // ownership itself is separately re-checked by stream-service via X-User-Id.
        folderService.verifyAndGetFolder(userId, folderPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        try {
            restTemplate.exchange(STREAM_SERVICE_VIDEOS_URL + "/" + videoId, HttpMethod.DELETE,
                    new HttpEntity<>(headers), Void.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this video");
        }
    }

    private ByteArrayResource toResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String safeFilename = sanitizeFilename(file.getOriginalFilename());
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return safeFilename;
                }
            };
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read uploaded file");
        }
    }

    // HTTP header field values must be ASCII (RFC 7230) — the browser's original
    // multipart request encodes non-ASCII filenames properly (RFC 5987), but that
    // gets decoded into a plain Java String by the time we have a MultipartFile, and
    // RestTemplate's multipart writer doesn't re-encode it when forwarding to
    // stream-service. A raw Unicode/emoji filename in the outgoing Content-Disposition
    // header there produces a malformed request that gets rejected before even
    // reaching stream-service's controller (no application log line, bare 400/500).
    private String sanitizeFilename(String filename) {
        if (filename == null) return "upload";
        return filename.replaceAll("[^\\x20-\\x7E]", "").replaceAll("[\"\\\\]", "").trim();
    }
}
