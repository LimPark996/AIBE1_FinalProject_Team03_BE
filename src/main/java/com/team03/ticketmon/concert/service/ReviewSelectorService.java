package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.domain.Review;
import com.team03.ticketmon.concert.util.TokenCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ReviewSelectorService — AI 프롬프트 토큰 한도 내로 리뷰를 선별
 * 전체 리뷰의 토큰 합이 한도를 넘으면 최신순으로 한도 내에서 채워서 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSelectorService {

	// TokenCalculator는 static 메서드로 변경되어 의존성 주입 불필요

	/**
	 * 토큰 제한을 고려하여 리뷰 선별
	 */
	public List<Review> selectReviewsWithinTokenLimit(List<Review> allReviews, int maxTokens) {

		// 1단계: 전체 토큰 수 계산 (static 메서드 호출)
		int totalTokens = TokenCalculator.calculateTotalTokens(allReviews);

		if (totalTokens <= maxTokens) {
			log.info("토큰 제한 내 - 전체 리뷰 사용: {}개, {}토큰",
				allReviews.size(), totalTokens);
			return allReviews;
		}

		// 2단계: 토큰 초과 시 선별 로직
		log.warn("토큰 제한 초과 - 리뷰 선별 시작: 전체 {}개({}토큰) -> 목표 {}토큰",
			allReviews.size(), totalTokens, maxTokens);

		return selectByLatestFirst(allReviews, maxTokens);
	}

	/**
	 * 최신순으로 토큰 제한까지 리뷰 선별
	 */
	private List<Review> selectByLatestFirst(List<Review> reviews, int maxTokens) {
		// 최신순 정렬 (이미 Repository에서 정렬되어 있지만 보장)
		List<Review> sortedReviews = reviews.stream()
			.sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
			.collect(Collectors.toList());

		List<Review> selectedReviews = new java.util.ArrayList<>();
		int accumulatedTokens = 0;

		for (Review review : sortedReviews) {
			List<Review> tempList = new java.util.ArrayList<>(selectedReviews);
			tempList.add(review);

			int newTokens = TokenCalculator.calculateTotalTokens(tempList);

			if (newTokens <= maxTokens) {
				selectedReviews.add(review);
				accumulatedTokens = newTokens;
			} else {
				break; // 토큰 제한 초과 시 중단
			}
		}

		log.info("리뷰 선별 완료: {}개 -> {}개 ({}토큰)",
			reviews.size(), selectedReviews.size(), accumulatedTokens);

		return selectedReviews;
	}
}