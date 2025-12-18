package com.team03.ticketmon.batch.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "batch_execution_log")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobName;  // "AI_SUMMARY", "CONCERT_OPEN", "CONCERT_CLOSE", "CONCERT_COMPLETE"

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;

    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private Integer skipCount;

    @Enumerated(EnumType.STRING)
    private BatchStatus status;  // SUCCESS, PARTIAL_FAIL, FAIL

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}