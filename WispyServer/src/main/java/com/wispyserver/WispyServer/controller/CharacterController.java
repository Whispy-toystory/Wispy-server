package com.wispyserver.WispyServer.controller;

import com.wispyserver.WispyServer.dto.request.CreateCharacterRequest;
import com.wispyserver.WispyServer.dto.response.ApiResponse;
import com.wispyserver.WispyServer.dto.response.CharacterListResponse;
import com.wispyserver.WispyServer.dto.response.CharacterResponse;
import com.wispyserver.WispyServer.dto.response.GlbUploadResponse;
import com.wispyserver.WispyServer.exception.CustomException;
import com.wispyserver.WispyServer.service.CharacterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

            log.info("POST /api/characters - User ID: {}, Request: {}",
                    userId, request);
            log.info("Request details - userName: {}, userBirthDate: {}, characterName: {}",
                    request.getUserName(), request.getUserBirthDate(), request.getCharacterName());

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
                    "Missing or invalid character creation data: " + e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CharacterListResponse>>> getCharacterList(
            Authentication authentication) {

        try {
            String userIdStr = authentication.getName();
            UUID userId = UUID.fromString(userIdStr);

            log.info("GET /api/characters - User ID: {}", userId);

            List<CharacterListResponse> characterList = characterService.getCharacterList(userId);

            ApiResponse<List<CharacterListResponse>> apiResponse = ApiResponse.success(
                    characterList,
                    "Character list fetched successfully"
            );

            return ResponseEntity.ok(apiResponse);

        } catch (CustomException e) {
            log.error("Character list fetch failed: {}", e.getMessage());

            ApiResponse<List<CharacterListResponse>> errorResponse = ApiResponse.error(
                    e.getErrorCode(),
                    e.getMessage()
            );
            return ResponseEntity.status(e.getStatusCode()).body(errorResponse);

        } catch (Exception e) {
            log.error("Unexpected error during character list fetch", e);

            ApiResponse<List<CharacterListResponse>> errorResponse = ApiResponse.error(
                    "CHARACTER_LIST_FETCH_FAILED",
                    "Failed to fetch character list: " + e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/{characterId}/upload-glb")
    public ResponseEntity<ApiResponse<GlbUploadResponse>> uploadGlbFile(
            @PathVariable("characterId") UUID characterId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        try {
            String userIdStr = authentication.getName();
            UUID userId = UUID.fromString(userIdStr);

            log.info("POST /api/characters/{}/upload-glb - User ID: {}, File: {}",
                    characterId, userId, file.getOriginalFilename());

            if (file == null || file.isEmpty()) {
                ApiResponse<GlbUploadResponse> errorResponse = ApiResponse.error(
                        "NO_FILE_UPLOADED",
                        "No file was uploaded"
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            GlbUploadResponse response = characterService.uploadGlbFile(userId, characterId, file);

            ApiResponse<GlbUploadResponse> apiResponse = ApiResponse.success(
                    response,
                    "GLB file uploaded successfully"
            );

            return ResponseEntity.ok(apiResponse);

        } catch (CustomException e) {
            log.error("GLB file upload failed: {}", e.getMessage());

            String errorCode = e.getErrorCode();
            if ("CHARACTER_NOT_FOUND".equals(errorCode)) {
                ApiResponse<GlbUploadResponse> errorResponse = ApiResponse.error(
                        "CHARACTER_NOT_FOUND",
                        "Character not found"
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            ApiResponse<GlbUploadResponse> errorResponse = ApiResponse.error(
                    e.getErrorCode(),
                    e.getMessage()
            );
            return ResponseEntity.status(e.getStatusCode()).body(errorResponse);

        } catch (Exception e) {
            log.error("Unexpected error during GLB file upload", e);

            ApiResponse<GlbUploadResponse> errorResponse = ApiResponse.error(
                    "FILE_UPLOAD_FAILED",
                    "Failed to upload GLB file: " + e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}