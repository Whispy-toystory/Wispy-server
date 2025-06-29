package com.wispyserver.WispyServer.service;

import com.wispyserver.WispyServer.dto.request.GuestAccountRequest;
import com.wispyserver.WispyServer.dto.response.GuestAccountResponse;
import com.wispyserver.WispyServer.entity.User;
import com.wispyserver.WispyServer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
}