package com.wispyserver.WispyServer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GustAccountResponse {

    private UUID userId;

    private String accessToken;

    private String refreshToken;

    private Long expiresIn;

    private Long refreshExpireseIn;

    private LocalDateTime createdAt;

}
