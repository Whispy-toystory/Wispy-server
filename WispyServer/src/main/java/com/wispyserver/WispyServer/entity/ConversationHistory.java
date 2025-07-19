package com.wispyserver.WispyServer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_histories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID characterId;

    @Column(nullable = false, length = 1000)
    private String userInput;

    @Column(nullable = false, length = 2000)
    private String aiResponse;

    @Column(nullable = false)
    private LocalDateTime createdAt;

}