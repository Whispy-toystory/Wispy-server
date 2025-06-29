package com.wispyserver.WispyServer.controller;

import com.wispyserver.WispyServer.dto.request.GuestAccountRequest;
import com.wispyserver.WispyServer.dto.response.ApiResponse;
import com.wispyserver.WispyServer.dto.response.GuestAccountResponse;
import com.wispyserver.WispyServer.service.AuthService;
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

        log.info("POST /api/auth/guset - Device: {}, Platform: {}",
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
}
