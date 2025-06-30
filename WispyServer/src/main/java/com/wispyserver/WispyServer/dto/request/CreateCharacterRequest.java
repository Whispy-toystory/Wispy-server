package com.wispyserver.WispyServer.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate userBirthDate;

    @NotBlank(message = "Character name is required")
    private String characterName;
}
