package com.wispyserver.WispyServer.repository;

import com.wispyserver.WispyServer.entity.Character;
import com.wispyserver.WispyServer.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CharacterRepository extends JpaRepository<Character, UUID> {

    List<Character> findByUserAndIsActiveTrue(User user);

    List<Character> findByUserUserIdAndIsActiveTrue(UUID userId);

    boolean existsByUserAndCharacterNameAndIsActiveTrue(User user, String characterName);

    long countByUserAndIsActiveTrue(User user);

    @Query("SELECT c.characterSlot FROM Character c WHERE c.user = :user AND c.isActive = true ORDER BY c.characterSlot")
    List<Integer> findUsedSlotsByUser(@Param("user") User user);
}