package com.wispyserver.WispyServer.controller;

import com.wispyserver.WispyServer.dto.request.GuestAccountRequest;
import com.wispyserver.WispyServer.dto.request.RefreshTokenRequest;
import com.wispyserver.WispyServer.dto.response.ApiResponse;
import com.wispyserver.WispyServer.dto.response.GuestAccountResponse;
import com.wispyserver.WispyServer.dto.response.RefreshTokenResponse;
import com.wispyserver.WispyServer.service.AuthService;
import com.wispyserver.WispyServer.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<GuestAccountResponse>> createGuestAccount(
            @Valid @RequestBody GuestAccountRequest request) {

        log.info("POST /api/auth/guest - Device: {}, Platform: {}",
                request.getDeviceId(), request.getPlatform());

        try {
            GuestAccountResponse response = authService.createGuestAccount(request);

            ApiResponse<GuestAccountResponse> apiResponse = ApiResponse.success(
                    response,
                    "Account created successfully"
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
        } catch (Exception e) {
            log.error("Error creating guest account", e);

            ApiResponse<GuestAccountResponse> errorResponse = ApiResponse.error(
                    "ACCOUNT_CREATION_FAILED",
                    e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshAccessToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        log.info("POST /api/auth/token/refresh - Device: {}, Platform: {}",
                request.getDeviceId(), request.getPlatform());

        try {
            String refreshToken = JwtTokenUtil.extractTokenFromRequest(httpRequest);

            if (refreshToken == null) {
                ApiResponse<RefreshTokenResponse> errorResponse = ApiResponse.error(
                        "INVALID_REFRESH_TOKEN",
                        "Refresh token is missing in Authorization header"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            RefreshTokenResponse response = authService.refreshAccessToken(refreshToken, request);

            ApiResponse<RefreshTokenResponse> apiResponse = ApiResponse.success(
                    response,
                    "Access token refreshed successfully"
            );

            return ResponseEntity.ok(apiResponse);

        } catch (Exception e) {
            log.error("Error refreshing acces token", e);

            if (e.getMessage().contains("invalid") || e.getMessage().contains("expired") ||
                    e.getMessage().contains("does not match")) {

                ApiResponse<RefreshTokenResponse> errorResponse = ApiResponse.error(
                        "INVALID_REFRESH_TOKEN",
                        "Refresh token is invalid or expired"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            if (e.getMessage().contains("not found")) {
                ApiResponse<RefreshTokenResponse> errorResponse = ApiResponse.error(
                        "USER_NOT_FOUND",
                        "User not found"
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            ApiResponse<RefreshTokenResponse> errorResponse = ApiResponse.error(
                    "TOKEN_REFRESH_FAILED",
                    e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        }
    }
}
