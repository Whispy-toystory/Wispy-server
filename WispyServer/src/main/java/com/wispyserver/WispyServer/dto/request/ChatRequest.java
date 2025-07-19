package com.wispyserver.WispyServer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "User input cannot be empty")
    @Size(max = 1000, message = "User input cannot exceed 1000 characters")
    private String userInput;

}