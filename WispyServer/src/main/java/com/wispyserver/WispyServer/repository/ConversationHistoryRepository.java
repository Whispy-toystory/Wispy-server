package com.wispyserver.WispyServer.repository;

import com.wispyserver.WispyServer.entity.ConversationHistory;
import com.wispyserver.WispyServer.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

import java.awt.print.Pageable;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ConversationHistoryRepository extends JpaRepository<ConversationHistory, UUID> {

    @Query("SELECT ch FROM ConversationHistory ch " +
            "WHERE ch.user = :user AND ch.character = :character " +
            "ORDER BY ch.createdAt DESC")
    List<ConversationHistory> findRecentConversations(
            @Param("user") User user,
            @Param("character") Character character,
            Pageable pageable);

    @Query("SELECT ch FROM ConversationHistory ch " +
            "WHERE ch.user = :user AND ch.character = :character " +
            "AND DATE(ch.createdAt) = :date " +
            "ORDER BY ch.createdAt")
    List<ConversationHistory> findConversationsByDate(
            @Param("user") User user,
            @Param("character") Character character,
            @Param("date") LocalDate date);

}