package com.team03.ticketmon.concert.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * ReviewChangeDetectionDTO — AI 요약 업데이트 필요 여부 판정 결과 DTO
 * 현재/이전 리뷰 수·체크섬과 함께 needsUpdate 플래그 및 판정 사유를 담는다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewChangeDetectionDTO {
	private Long concertId;
	private Integer currentReviewCount;
	private Integer lastSummaryReviewCount;
	private String currentReviewChecksum;
	private String lastSummaryChecksum;
	private LocalDateTime lastReviewModifiedAt;
	private LocalDateTime aiSummaryGeneratedAt;
	private Boolean needsUpdate;
	private String changeReason; // "COUNT_CHANGED", "CONTENT_CHANGED", "NEW_REVIEWS_ADDED"
}