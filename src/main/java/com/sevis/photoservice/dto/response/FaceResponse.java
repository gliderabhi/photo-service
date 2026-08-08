package com.sevis.photoservice.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FaceResponse {
    private Long id;
    private Long photoId;
    private Long personId;
    /** All fractions (0..1) of the photo's width/height. */
    private Double boxTop;
    private Double boxRight;
    private Double boxBottom;
    private Double boxLeft;
}
