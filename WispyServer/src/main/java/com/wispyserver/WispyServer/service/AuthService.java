package com.wispyserver.WispyServer.service;

import com.wispyserver.WispyServer.dto.request.GuestAccountRequest;
import com.wispyserver.WispyServer.dto.request.RefreshTokenRequest;
import com.wispyserver.WispyServer.dto.response.GuestAccountResponse;
import com.wispyserver.WispyServer.dto.response.RefreshTokenResponse;
import com.wispyserver.WispyServer.entity.User;
import com.wispyserver.WispyServer.exception.CustomException;
import com.wispyserver.WispyServer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional
    public GuestAccountResponse createGuestAccount(GuestAccountRequest request) {
        log.info("Creating guest account for device: {}", request.getDeviceId());

        if (userRepository.existsByDeviceId(request.getDeviceId())) {
            throw new RuntimeException("Device already registered");
        }

        User user = User.builder()
                .deviceId(request.getDeviceId())
                .platform(User.Platform.fromString(request.getPlatform()))
                .appVersion(request.getAppVersion())
                .isGuest(true)
                .isActive(true)
                .lastLoginAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(savedUser.getUserId());
        String refreshToken = jwtService.generateRefreshToken(savedUser.getUserId());

        savedUser.setRefreshToken(refreshToken);
        savedUser.setRefreshTokenExpiresAt(
                LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiration())
        );
        userRepository.save(savedUser);

        log.info("Guest account created successfully. User ID: {}", savedUser.getUserId());

        return GuestAccountResponse.builder()
                .userId(savedUser.getUserId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpiration())
                .refreshExpiresIn(jwtService.getRefreshTokenExpiration())
                .createdAt(savedUser.getCreatedAt())
                .build();
    }

    @Transactional
    public RefreshTokenResponse refreshAccessToken(String refreshToken, RefreshTokenRequest request) {
        log.info("Refreshing access token for device: {}", request.getDeviceId());

        if (!jwtService.isTokenValid(refreshToken)) {
            throw CustomException.unauthorized("Refresh token is invalid or expired");
        }

        UUID userId = jwtService.getUserIdFromToken(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> CustomException.notFound("User not found"));

        if (!refreshToken.equals(user.getRefreshToken())) {
            throw CustomException.unauthorized("Refresh token does not match");
        }

        if (user.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw CustomException.unauthorized("Refresh token has expired");
        }
        //-> 이 경우엔 재 로그인 해야 함!

        if (!user.getDeviceId().equals(request.getDeviceId()) ||
                !user.getPlatform().getValue().equals(request.getPlatform())) {
            throw CustomException.unauthorized("Device information does not match");
        }

        if (!user.getIsActive()) {
            throw CustomException.unauthorized("User account is inactive");
        }

        String newAccessToken = jwtService.generateAccessToken(userId);
        String newRefreshToken = jwtService.generateRefreshToken(userId);

        user.setRefreshToken(newRefreshToken);
        user.setRefreshTokenExpiresAt(
                LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiration())
        );
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Access token refreshed successfully. User ID: {}", userId);

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtService.getAccessTokenExpiration())
                .refreshExpiresIn(jwtService.getRefreshTokenExpiration())
                .createdAt(LocalDateTime.now())
                .build();
    }
}