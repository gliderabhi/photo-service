package com.sevis.photoservice.service;

import com.sevis.photoservice.dto.faceservice.FaceServiceDetectResponse;
import com.sevis.photoservice.dto.request.RenamePersonRequest;
import com.sevis.photoservice.dto.response.FaceResponse;
import com.sevis.photoservice.dto.response.PersonResponse;
import com.sevis.photoservice.dto.response.PhotoResponse;
import com.sevis.photoservice.model.Face;
import com.sevis.photoservice.model.Person;
import com.sevis.photoservice.model.Photo;
import com.sevis.photoservice.repository.FaceRepository;
import com.sevis.photoservice.repository.PersonRepository;
import com.sevis.photoservice.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Face detection + "same person" clustering, run against the plaintext image
 * bytes PhotoService.upload() has in hand right before it encrypts them to
 * disk — face-service never sees encrypted bytes or the folder password.
 *
 * Clustering matches a new face against every one of a person's existing
 * stored faces (capped per person, see MAX_EXEMPLARS_PER_PERSON) and takes
 * the best of those — not a single blended centroid. A centroid drifts
 * towards the "average" of every angle/lighting/expression folded into it,
 * which in practice made the same real person's later photos fall outside
 * the match threshold and get split off as a new Person; matching against
 * the individual exemplars doesn't have that failure mode. Mirrors the
 * approach the app's previous on-device implementation used, ported here now
 * that detection itself is server-side. No naming/identity is inferred; the
 * user labels people manually via renamePerson().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceService {

    // face_recognition's own published match threshold for its 128-d
    // encodings (Euclidean distance) — not tuned against this app's library,
    // but the right starting point since face-service uses that same model.
    private static final double CLUSTER_DISTANCE_THRESHOLD = 0.6;

    // Bounds how many of a person's stored faces a new face gets compared
    // against. Without this, a person's cluster keeps gaining more chances at
    // a spurious high-similarity hit purely from face count as it grows, not
    // genuine resemblance — classic extreme-value inflation in a "best of N
    // comparisons" scheme. Also keeps per-upload matching cost bounded
    // regardless of how large one person's cluster or the library gets.
    private static final int MAX_EXEMPLARS_PER_PERSON = 50;

    private final FaceRepository faceRepository;
    private final PersonRepository personRepository;
    private final PhotoRepository photoRepository;
    private final RestTemplate faceServiceRestTemplate;

    @Value("${face.service.url}")
    private String faceServiceUrl;

    @Async("faceDetectionExecutor")
    public void detectAndStoreAsync(Long userId, Long photoId, byte[] imageBytes, String originalFilename) {
        try {
            FaceServiceDetectResponse detection = callFaceService(imageBytes, originalFilename);
            if (detection == null || detection.getFaces() == null || detection.getFaces().isEmpty()) return;

            // Every existing exemplar for this user, grouped by person and capped
            // per person — loaded once per photo (not once per face) and appended
            // to in-memory as faces in *this* photo get assigned, so multiple faces
            // of the same person within one photo still cluster together correctly.
            Map<Long, List<double[]>> exemplars = loadExemplars(userId);

            for (FaceServiceDetectResponse.DetectedFace detected : detection.getFaces()) {
                double[] embedding = detected.getEmbedding().stream().mapToDouble(Double::doubleValue).toArray();

                Face face = new Face();
                face.setUserId(userId);
                face.setPhotoId(photoId);
                face.setBoxTop(detected.getBox().getTop() / (double) detection.getImageHeight());
                face.setBoxBottom(detected.getBox().getBottom() / (double) detection.getImageHeight());
                face.setBoxLeft(detected.getBox().getLeft() / (double) detection.getImageWidth());
                face.setBoxRight(detected.getBox().getRight() / (double) detection.getImageWidth());
                face.setEmbedding(joinEmbedding(embedding));

                Person person = assignPerson(userId, embedding, exemplars);
                face.setPersonId(person.getId());
                faceRepository.save(face);

                if (person.getCoverFaceId() == null) {
                    person.setCoverFaceId(face.getId());
                    personRepository.save(person);
                }
            }
        } catch (Exception e) {
            // Best-effort: a face-service outage or a bad image must never surface
            // as an upload failure to the user — the photo itself already saved fine.
            log.warn("Face detection failed for photo {}: {}", photoId, e.getMessage());
        }
    }

    private Map<Long, List<double[]>> loadExemplars(Long userId) {
        Map<Long, List<double[]>> exemplars = new HashMap<>();
        for (Face face : faceRepository.findByUserIdAndPersonIdIsNotNull(userId)) {
            List<double[]> personExemplars = exemplars.computeIfAbsent(face.getPersonId(), k -> new ArrayList<>());
            if (personExemplars.size() < MAX_EXEMPLARS_PER_PERSON) {
                personExemplars.add(parseEmbedding(face.getEmbedding()));
            }
        }
        return exemplars;
    }

    private FaceServiceDetectResponse callFaceService(byte[] imageBytes, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename != null ? filename : "upload.jpg";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return faceServiceRestTemplate.postForObject(
                faceServiceUrl + "/detect", new HttpEntity<>(body, headers), FaceServiceDetectResponse.class);
    }

    // Not @Transactional: this is called from detectAndStoreAsync in the same
    // class, so a self-invoked annotation would silently no-op (same proxy
    // caveat as PhotoService's @Cacheable self-injection). Each repository
    // save() below is already transactional on its own; a non-atomic
    // read-then-write pair here is an acceptable tradeoff for best-effort
    // clustering, not a correctness risk for photo data itself.
    private Person assignPerson(Long userId, double[] embedding, Map<Long, List<double[]>> exemplars) {
        // Best match is whichever person has the single closest exemplar among
        // their stored faces — not whichever person's *average* is closest, which
        // is what makes this robust to one person having very different-looking
        // exemplars (angle/lighting/expression) rather than one blurry average.
        Long bestPersonId = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<Long, List<double[]>> entry : exemplars.entrySet()) {
            for (double[] exemplar : entry.getValue()) {
                double distance = euclideanDistance(embedding, exemplar);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestPersonId = entry.getKey();
                }
            }
        }

        if (bestPersonId != null && bestDistance < CLUSTER_DISTANCE_THRESHOLD) {
            final Long matchedPersonId = bestPersonId;
            Person person = personRepository.findByIdAndUserId(matchedPersonId, userId)
                    .orElseThrow(() -> new IllegalStateException("Person " + matchedPersonId + " vanished mid-detection"));
            person.setFaceCount(person.getFaceCount() + 1);
            personRepository.save(person);

            List<double[]> personExemplars = exemplars.computeIfAbsent(matchedPersonId, k -> new ArrayList<>());
            if (personExemplars.size() < MAX_EXEMPLARS_PER_PERSON) personExemplars.add(embedding);
            return person;
        }

        Person person = new Person();
        person.setUserId(userId);
        person.setFaceCount(1);
        person = personRepository.save(person);
        exemplars.put(person.getId(), new ArrayList<>(List.of(embedding)));
        return person;
    }

    public List<FaceResponse> facesForPhoto(Long userId, Long photoId) {
        return faceRepository.findByPhotoIdAndUserId(photoId, userId).stream()
                .map(this::toFaceResponse)
                .collect(Collectors.toList());
    }

    public List<PersonResponse> listPeople(Long userId) {
        return personRepository.findByUserId(userId).stream()
                .map(this::toPersonResponse)
                .collect(Collectors.toList());
    }

    public List<PhotoResponse> photosForPerson(Long userId, Long personId) {
        personRepository.findByIdAndUserId(personId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found"));

        List<Long> photoIds = faceRepository.findByPersonIdAndUserId(personId, userId).stream()
                .map(Face::getPhotoId)
                .distinct()
                .collect(Collectors.toList());

        List<PhotoResponse> responses = new ArrayList<>();
        for (Long photoId : photoIds) {
            photoRepository.findByIdAndUserId(photoId, userId).ifPresent(photo -> responses.add(toPhotoResponse(photo)));
        }
        return responses;
    }

    @Transactional
    public PersonResponse renamePerson(Long userId, Long personId, RenamePersonRequest request) {
        Person person = personRepository.findByIdAndUserId(personId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found"));
        person.setLabel(request.getLabel());
        return toPersonResponse(personRepository.save(person));
    }

    // Called from PhotoService.delete/bulkDelete. Known simplification: a
    // Person's faceCount/centroid and coverFaceId are not recomputed when one
    // of its faces is deleted this way — a person can end up pointing at a
    // stale coverFaceId or an inflated faceCount. Harmless for now (worst case
    // is a missing thumbnail, handled by the null check in toPersonResponse),
    // revisit if person accuracy starts to matter more than it does today.
    public void deleteFacesForPhoto(Long photoId) {
        faceRepository.deleteByPhotoId(photoId);
    }

    private FaceResponse toFaceResponse(Face face) {
        return FaceResponse.builder()
                .id(face.getId())
                .photoId(face.getPhotoId())
                .personId(face.getPersonId())
                .boxTop(face.getBoxTop())
                .boxRight(face.getBoxRight())
                .boxBottom(face.getBoxBottom())
                .boxLeft(face.getBoxLeft())
                .build();
    }

    private PersonResponse toPersonResponse(Person person) {
        PersonResponse.PersonResponseBuilder builder = PersonResponse.builder()
                .id(person.getId())
                .label(person.getLabel())
                .faceCount(person.getFaceCount());

        if (person.getCoverFaceId() != null) {
            faceRepository.findById(person.getCoverFaceId()).ifPresent(cover -> builder
                    .coverPhotoId(cover.getPhotoId())
                    .coverBoxTop(cover.getBoxTop())
                    .coverBoxRight(cover.getBoxRight())
                    .coverBoxBottom(cover.getBoxBottom())
                    .coverBoxLeft(cover.getBoxLeft()));
        }
        return builder.build();
    }

    private PhotoResponse toPhotoResponse(Photo photo) {
        return PhotoResponse.builder()
                .id(photo.getId())
                .originalFilename(photo.getOriginalFilename())
                .contentType(photo.getContentType())
                .fileSize(photo.getFileSize())
                .uploadedAt(photo.getUploadedAt())
                .url("/api/photos/" + photo.getId() + "/content")
                .build();
    }

    private static double euclideanDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private static double[] parseEmbedding(String csv) {
        String[] parts = csv.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Double.parseDouble(parts[i]);
        return result;
    }

    private static String joinEmbedding(double[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.toString();
    }
}
