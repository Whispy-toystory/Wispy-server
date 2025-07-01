package com.wispyserver.WispyServer.repository;

import com.wispyserver.WispyServer.entity.Character;
import com.wispyserver.WispyServer.entity.ConversationSummary;
import com.wispyserver.WispyServer.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, UUID> {

    @Query("SELECT cs FROM ConversationSummary cs " +
            "WHERE cs.user = :user AND cs.character = :character " +
            "ORDER BY cs.summaryDate DESC")
    Optional<ConversationSummary> findLatestSummaryByUserAndCharacter(
            @Param("user") User user,
            @Param("character") Character character);

    Optional<ConversationSummary> findByUserAndCharacterAndSummaryDate(
            User user, Character character, LocalDate summaryDate);

    @Query("SELECT cs FROM ConversationSummary cs " +
            "WHERE cs.user = :user AND cs.character = :character " +
            "AND cs.summaryDate = :yesterday")
    Optional<ConversationSummary> findYesterdaySummary(
            @Param("user") User user,
            @Param("character") Character character,
            @Param("yesterday") LocalDate yesterday);
}