package com.sevis.photoservice.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class AddPhotosToAlbumRequest {
    private List<Long> photoIds;
}
