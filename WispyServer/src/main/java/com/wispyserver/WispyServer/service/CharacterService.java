package com.wispyserver.WispyServer.service;

import com.wispyserver.WispyServer.dto.request.CreateCharacterRequest;
import com.wispyserver.WispyServer.dto.response.CharacterListResponse;
import com.wispyserver.WispyServer.dto.response.CharacterResponse;
import com.wispyserver.WispyServer.dto.response.CharacterSelectResponse;
import com.wispyserver.WispyServer.dto.response.GlbUploadResponse;
import com.wispyserver.WispyServer.entity.Character;
import com.wispyserver.WispyServer.entity.ConversationSummary;
import com.wispyserver.WispyServer.entity.User;
import com.wispyserver.WispyServer.exception.CustomException;
import com.wispyserver.WispyServer.repository.CharacterRepository;
import com.wispyserver.WispyServer.repository.ConversationSummaryRepository;
import com.wispyserver.WispyServer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;
    private final ConversationSummaryRepository conversationSummaryRepository;
    private final S3Service s3Service;


    private static final long MAX_CHARACTERS_PER_USER = 4;

    @Transactional
    public CharacterResponse createCharacter(UUID userId, CreateCharacterRequest request) {
        log.info("Creating character for user: {}, character name: {}", userId, request.getCharacterName());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> CustomException.notFound("User not found"));

        if (!user.getIsActive()) {
            throw CustomException.badRequest("User account is inactive");
        }

        long characterCount = characterRepository.countByUserAndIsActiveTrue(user);
        if (characterCount >= MAX_CHARACTERS_PER_USER) {
            throw CustomException.characterLimitExceeded(
                    String.format("Maximum number of characters (%d) reached", MAX_CHARACTERS_PER_USER)
            );
        }

        user.setUsername(request.getUserName());
        user.setUserBirthDate(request.getUserBirthDate());
        userRepository.save(user);

        Integer slot = findNextAvailableSlot(user);
        if (slot == null) {
            throw CustomException.characterLimitExceeded("No available character slot");
        }

        Character character = Character.builder()
                .characterName(request.getCharacterName())
                .characterSlot(slot)
                .createdDate(LocalDate.now())
                .isActive(true)
                .user(user)
                .glbUrl(null)
                .build();

        Character savedCharacter = characterRepository.save(character);

        log.info("Character created successfully. Character ID: {}, User ID: {}",
                savedCharacter.getCharacterId(), userId);

        return CharacterResponse.builder()
                .characterId(savedCharacter.getCharacterId())
                .characterName(savedCharacter.getCharacterName())
                .characterSlot(slot)
                .glbUrl(savedCharacter.getGlbUrl())
                .createdDate(savedCharacter.getCreatedDate())
                .build();
    }

    @Transactional(readOnly = true)
    public List<CharacterListResponse> getCharacterList(UUID userId) {
        log.info("Fetching character list for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> CustomException.notFound("User not found"));

        if (!user.getIsActive()) {
            throw CustomException.badRequest("User account is inactive");
        }

        List<Character> characters = characterRepository.findByUserAndIsActiveTrueOrderByCharacterSlot(user);

        log.info("Found {} characters for user {}", characters.size(), userId);

        return characters.stream()
                .map(character -> CharacterListResponse.builder()
                        .characterId(character.getCharacterId())
                        .characterName(character.getCharacterName())
                        .characterSlot(character.getCharacterSlot())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public GlbUploadResponse uploadGlbFile(UUID userId, UUID characterId, MultipartFile file) {
        log.info("Uploading GLB file for character: {}, user: {}", characterId, userId);

        try {
            s3Service.validateGlbFile(file);

            Character character = characterRepository.findById(characterId)
                    .orElseThrow(() -> CustomException.characterNotFound("Character not found"));

            if (!character.getUser().getUserId().equals(userId)) {
                throw CustomException.unauthorized("You do not have permission to upload files for this character");
            }

            if (!character.getIsActive()) {
                throw CustomException.badRequest("Character is not active");
            }

            String glbUrl = s3Service.uploadGlbFile(characterId, file);

            character.setGlbUrl(glbUrl);
            Character updatedCharacter = characterRepository.save(character);

            log.info("GLB file uploaded successfully for character: {}, URL: {}",
                    characterId, glbUrl);

            return GlbUploadResponse.builder()
                    .characterId(updatedCharacter.getCharacterId())
                    .glbUrl(updatedCharacter.getGlbUrl())
                    .build();

        } catch (IOException e) {
            log.error("IOException while uploading GLB file for character: {}", characterId, e);
            throw CustomException.badRequest("Failed to upload GLB file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to upload GLB file for character: {}", characterId, e);

            if (e instanceof CustomException) {
                throw e;
            }
            throw CustomException.badRequest("Failed to upload GLB file: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public CharacterSelectResponse selectCharacter(UUID userId, UUID characterId) {
        log.info("Selecting character: {} for user: {}", characterId, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> CustomException.notFound("User not found"));

        if (!user.getIsActive()) {
            throw CustomException.badRequest("User account is inactive");
        }

        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> CustomException.characterNotFound("Character not found"));

        if (!character.getUser().getUserId().equals(userId)) {
            throw CustomException.unauthorized("You do not have permission to select this character");
        }

        if (!character.getIsActive()) {
            throw CustomException.badRequest("Character is not active");
        }

        CharacterSelectResponse.UserInfo userInfo = CharacterSelectResponse.UserInfo.builder()
                .name(user.getUsername())
                .birthDate(user.getUserBirthDate() != null ?
                        user.getUserBirthDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null)
                .build();

        boolean isReady = character.getGlbUrl() != null && !character.getGlbUrl().trim().isEmpty();
        CharacterSelectResponse.CharacterInfo characterInfo = CharacterSelectResponse.CharacterInfo.builder()
                .characterId(character.getCharacterId().toString())
                .name(character.getCharacterName())
                .glbUrl(character.getGlbUrl())
                .isReady(isReady)
                .build();

        CharacterSelectResponse.SummaryInfo summaryInfo = getYesterdaySummary(user, character);

        log.info("Character selected successfully. Character: {}, Ready: {}, Summary exists: {}",
                character.getCharacterName(), isReady, summaryInfo != null);

        return CharacterSelectResponse.builder()
                .user(userInfo)
                .character(characterInfo)
                .summary(summaryInfo)
                .build();
    }

    private CharacterSelectResponse.SummaryInfo getYesterdaySummary(User user, Character character) {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        Optional<ConversationSummary> summaryOptional =
                conversationSummaryRepository.findYesterdaySummary(user, character, yesterday);

        if (summaryOptional.isPresent()) {
            ConversationSummary summary = summaryOptional.get();
            return CharacterSelectResponse.SummaryInfo.builder()
                    .date(summary.getSummaryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    .text(summary.getSummaryText())
                    .build();
        }

        Optional<ConversationSummary> latestSummaryOptional =
                conversationSummaryRepository.findLatestSummaryByUserAndCharacter(user, character);

        if (latestSummaryOptional.isPresent()) {
            ConversationSummary summary = latestSummaryOptional.get();
            return CharacterSelectResponse.SummaryInfo.builder()
                    .date(summary.getSummaryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    .text(summary.getSummaryText())
                    .build();
        }

        return null;
    }


    private Integer findNextAvailableSlot(User user) {
        List<Integer> usedSlots = characterRepository.findUsedSlotsByUser(user);

        for (int slot = 1; slot <= MAX_CHARACTERS_PER_USER; slot++) {
            if (!usedSlots.contains(slot)) {
                return slot;
            }
        }
        return null;
    }
}
