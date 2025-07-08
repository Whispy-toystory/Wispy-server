package com.wispyserver.WispyServer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlbGenerateResponse {
    private String characterId;
    private String status;
    private String jobId;
    private String message;
    private String glbUrl;
}