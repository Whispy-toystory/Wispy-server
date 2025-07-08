package com.wispyserver.WispyServer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlbStatusResponse {
    private UUID jobId;
    private UUID characterId;
    private String status;
    private Integer progress;
    private String outputGlbUrl;
    private String errorMessage;
    private LocalDateTime estimatedCompletion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}