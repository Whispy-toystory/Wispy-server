package com.wispyserver.WispyServer.controller;

import com.wispyserver.WispyServer.dto.request.CreateCharacterRequest;
import com.wispyserver.WispyServer.dto.response.ApiResponse;
import com.wispyserver.WispyServer.dto.response.CharacterResponse;
import com.wispyserver.WispyServer.exception.CustomException;
import com.wispyserver.WispyServer.service.CharacterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
@Slf4j
public class CharacterController {

    private final CharacterService characterService;

    @PostMapping
    public ResponseEntity<ApiResponse<CharacterResponse>> createCharacter(
            @Valid @RequestBody CreateCharacterRequest request,
            Authentication authentication) {

        try {
            String userIdStr = authentication.getName();
            UUID userId = UUID.fromString(userIdStr);

            log.info("POST /api/characters - User ID: {}, Character name: {}",
                    userId, request.getCharacterName());

            CharacterResponse response = characterService.createCharacter(userId, request);

            ApiResponse<CharacterResponse> apiResponse = ApiResponse.success(
                    response,
                    "Character created successfully"
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);

        } catch (CustomException e) {
            log.error("Character creation failed: {}", e.getMessage());

            if (e.getErrorCode().equals("CHARACTER_LIMIT_EXCEEDED")) {
                ApiResponse<CharacterResponse> errorResponse = ApiResponse.error(
                        "CHARACTER_LIMIT_EXCEEDED",
                        e.getMessage()
                );
                return ResponseEntity.status(e.getStatusCode()).body(errorResponse);
            }

            ApiResponse<CharacterResponse> errorResponse = ApiResponse.error(
                    e.getErrorCode(),
                    e.getMessage()
            );
            return ResponseEntity.status(e.getStatusCode()).body(errorResponse);

        } catch (Exception e) {
            log.error("Unexpected error during character creation", e);

            ApiResponse<CharacterResponse> errorResponse = ApiResponse.error(
                    "INVALID_CHARACTER_DATA",
                    "Missing or invalid character creation data"
            );

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
}