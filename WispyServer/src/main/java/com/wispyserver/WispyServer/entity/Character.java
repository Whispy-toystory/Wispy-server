package com.wispyserver.WispyServer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "characters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "character_id", columnDefinition = "BINARY(16)")
    private UUID characterId;

    @Column(name = "character_name", nullable = false)
    private String characterName;

    @Column(name = "character_slot")
    private Integer characterSlot;

    @Column(name = "glb_url")
    private String glbUrl;

    @Column(name = "created_date", nullable = false)
    private LocalDate createdDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "glb_generation_status")
    private String glbGenerationStatus;

    @Column(name = "glb_generation_job_id")
    private String glbGenerationJobId;
}