package com.team03.ticketmon.concert.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.Review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

/**
 * AiSummaryService — 리뷰 리스트로부터 Spring AI ChatClient를 호출해 요약문을 생성
 *
 * 동작 흐름:
 *   1. 리뷰 유효성 검증 (null/empty, 최대 개수)
 *   2. ReviewSelectorService로 토큰 한도 내 리뷰 선별
 *   3. 프롬프트 작성 후 ChatClient 호출
 *   4. 응답 후처리(길이 검증, 공백 정리, 최대 길이 절단)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

	private final ChatClient chatClient;  // Spring AI가 주입
	private final ReviewSelectorService reviewSelectorService;

	private static final int MAX_REVIEWS = 100;
	private static final int MIN_SUMMARY_LENGTH = 30;
	private static final int MAX_SUMMARY_LENGTH = 1500;

	public String generateSummary(List<Review> reviews) {
		// 1. 검증
		validateReviews(reviews);
		log.info("AI 리뷰 요약 생성 시작 - 리뷰 개수: {}", reviews.size());

		// 2. 리뷰 선별 (토큰 제한)
		List<Review> selected = reviewSelectorService
				.selectReviewsWithinTokenLimit(reviews, 4000);

		// 3. 프롬프트 생성
		String prompt = buildPrompt(selected);

		// 4. AI 호출
		String summary = chatClient.prompt()
				.user(prompt)
				.call()
				.content();

		// 5. 후처리
		return validateAndClean(summary);
	}

	private void validateReviews(List<Review> reviews) {
		if (reviews == null || reviews.isEmpty()) {
			throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND, "요약할 리뷰가 없습니다.");
		}
		if (reviews.size() > MAX_REVIEWS) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"최대 " + MAX_REVIEWS + "개까지 처리 가능합니다.");
		}
	}

	private String buildPrompt(List<Review> reviews) {
		StringBuilder sb = new StringBuilder();
		sb.append("다음 콘서트 후기들을 종합하여 요약해주세요.\n\n");

		for (int i = 0; i < reviews.size(); i++) {
			Review r = reviews.get(i);
			sb.append("=== 후기 ").append(i + 1).append(" ===\n");
			if (r.getRating() != null) sb.append("평점: ").append(r.getRating()).append("/5\n");
			sb.append("내용: ").append(r.getDescription()).append("\n\n");
		}
		return sb.toString();
	}

	private String validateAndClean(String summary) {
		if (summary == null || summary.length() < MIN_SUMMARY_LENGTH) {
			throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "요약이 너무 짧습니다.");
		}
		summary = summary.trim().replaceAll("\\s+", " ");
		if (summary.length() > MAX_SUMMARY_LENGTH) {
			summary = summary.substring(0, MAX_SUMMARY_LENGTH) + "...";
		}
		return summary;
	}
}