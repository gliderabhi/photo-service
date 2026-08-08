package com.sevis.photoservice.dto.faceservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Mirrors face-service's POST /detect response shape (see face-service/main.py,
 *  which uses Python/pydantic snake_case field names). */
@Getter
@Setter
public class FaceServiceDetectResponse {
    @JsonProperty("image_width")
    private int imageWidth;
    @JsonProperty("image_height")
    private int imageHeight;
    private List<DetectedFace> faces;

    @Getter
    @Setter
    public static class DetectedFace {
        private Box box;
        private List<Double> embedding;
    }

    @Getter
    @Setter
    public static class Box {
        private int top;
        private int right;
        private int bottom;
        private int left;
    }
}
