package com.sevis.photoservice.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class PhotosByDateResponse {
    private LocalDate date;
    private List<PhotoResponse> photos;
}
