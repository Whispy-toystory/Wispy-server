package com.wispyserver.WispyServer.service;

import com.wispyserver.WispyServer.dto.request.CreateCharacterRequest;
import com.wispyserver.WispyServer.dto.response.CharacterResponse;
import com.wispyserver.WispyServer.entity.Character;
import com.wispyserver.WispyServer.entity.User;
import com.wispyserver.WispyServer.exception.CustomException;
import com.wispyserver.WispyServer.repository.CharacterRepository;
import com.wispyserver.WispyServer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;

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

        user.setUserName(request.getUserName());
        user.setUserBirthDate(request.getUserBirthDate());
        userRepository.save(user);

        Character character = Character.builder()
                .characterName(request.getCharacterName())
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
                .glbUrl(savedCharacter.getGlbUrl())
                .createdDate(savedCharacter.getCreatedDate())
                .build();
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