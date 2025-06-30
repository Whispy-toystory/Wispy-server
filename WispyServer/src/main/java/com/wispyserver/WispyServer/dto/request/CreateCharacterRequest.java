package com.wispyserver.WispyServer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateCharacterRequest {

    @NotBlank(message = "User name is required")
    private String userName;

    @NotNull(message = "User birth date is required")
    @Past(message = "Birth date must be in the past")
    private LocalDate userBirthDate;

    @NotBlank(message = "Character name is required")
    private String characterName;

}
