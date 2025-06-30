package com.wispyserver.WispyServer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private Platform platform;

    @Column(name = "app_version", nullable = false)
    private String appVersion;

    @Column(name = "is_guest", nullable = false)
    @Builder.Default
    private Boolean isGuest = true;

    @Column(name = "username")
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "provider")
    private String provider;

    @Column(name = "provider_id")
    private String providerId;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Character> characters = new ArrayList<>();

    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Platform {
        IOS("ios"),
        ANDROID("android");

        private final String value;

        Platform(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Platform fromString(String value) {
            for (Platform platform : Platform.values()) {
                if (platform.getValue().equalsIgnoreCase(value)) {
                    return platform;
                }
            }
            throw new IllegalArgumentException("Invalid platform: " + value);
        }
    }
}