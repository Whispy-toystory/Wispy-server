package com.wispyserver.WispyServer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class GuestAccountRequest {

    @NotBlank(message = "Device ID is required")
    private String deviceId;

    @NotBlank(message = "Platform is required")
    @Pattern(regexp = "^(ios|android)$", message = "Platform must be 'ios' or 'android'")
    private String platform;

    @NotBlank(message = "App version is required")
    private String appVersion;
}
