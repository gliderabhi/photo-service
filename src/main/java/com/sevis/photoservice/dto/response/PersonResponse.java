package com.sevis.photoservice.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PersonResponse {
    private Long id;
    private String label;
    private Integer faceCount;
    /** The cover face's photo+box, so the client can crop a thumbnail itself
     *  (via the existing /api/photos/{id}/content endpoint) without a
     *  separate face-thumbnail endpoint. Null only if the cover face was
     *  since deleted (e.g. its photo was removed). */
    private Long coverPhotoId;
    private Double coverBoxTop;
    private Double coverBoxRight;
    private Double coverBoxBottom;
    private Double coverBoxLeft;
}
