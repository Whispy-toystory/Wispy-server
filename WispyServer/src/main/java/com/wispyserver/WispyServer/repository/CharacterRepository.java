package com.wispyserver.WispyServer.repository;

import com.wispyserver.WispyServer.entity.Character;
import com.wispyserver.WispyServer.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CharacterRepository extends JpaRepository<Character, UUID> {

    List<Character> findByUserAndIsActiveTrue(User user);

    List<Character> findByUserUserIdAndIsActiveTrue(UUID userId);

    boolean existsByUserAndCharacterNameAndIsActiveTrue(User user, String characterName);

    long countByUserAndIsActiveTrue(User user);

}