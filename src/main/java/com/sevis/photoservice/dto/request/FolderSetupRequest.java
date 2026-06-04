package com.sevis.photoservice.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FolderSetupRequest {
    private String password;
    /** Required only when changing an existing password */
    private String currentPassword;
}
