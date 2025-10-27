package com.team03.ticketmon.concert.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 배치 요약 처리 결과를 담는 DTO
 */

@Getter
@Setter
public class AiBatchSummaryResultDTO {
	private int totalProcessed;      // 총 처리된 콘서트 수
	private int successCount;        // 성공한 처리 수
	private int failCount;           // 실패한 처리 수
	private LocalDateTime processedAt; // 처리 완료 시간

	// 전체 생성자
	public AiBatchSummaryResultDTO(int totalProcessed, int successCount,
		int failCount, LocalDateTime processedAt) {
		this.totalProcessed = totalProcessed;
		this.successCount = successCount;
		this.failCount = failCount;
		this.processedAt = processedAt;
	}

	// 처리 성공률을 계산하는 메서드
	public double getSuccessRate() {
		if (totalProcessed == 0) return 0.0;
		return (double) successCount / totalProcessed * 100.0;
	}

	@Override
	public String toString() {
		return String.format(
			"AiBatchSummaryResultDTO{totalProcessed=%d, successCount=%d, failCount=%d, processedAt=%s, successRate=%.2f%%}",
			totalProcessed, successCount, failCount, processedAt, getSuccessRate()
		);
	}
}