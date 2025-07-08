// GlbGenerationJob.java - GLB 생성 작업 상태 관리
package com.wispyserver.WispyServer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "glb_generation_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlbGenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "job_id", columnDefinition = "BINARY(16)")
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "progress", nullable = false)
    @Builder.Default
    private Integer progress = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "hunyuan_task_id")
    private String hunyuanTaskId;

    @Column(name = "output_glb_url")
    private String outputGlbUrl;

    @Column(name = "estimated_completion")
    private LocalDateTime estimatedCompletion;

    // Character와의 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    // User와의 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum JobStatus {
        PENDING,
        UPLOADING,
        PROCESSING,
        DOWNLOADING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}