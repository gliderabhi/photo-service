package com.sevis.photoservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Result of one PhotoService#scanFaceBatch call — [remaining] tells the client
 *  (see both platforms' AutoUpload) whether to expect another batch to still be
 *  waiting next time it calls, without needing to poll a separate status endpoint. */
@Getter
@Builder
@AllArgsConstructor
public class FaceScanBatchResponse {
    private int scanned;
    private long remaining;
}
