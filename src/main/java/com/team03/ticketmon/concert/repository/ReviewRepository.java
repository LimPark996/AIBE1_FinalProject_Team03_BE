package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.Review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * ReviewRepository — 후기(Review) 데이터 접근 계층
 * 페이징 조회와 AI 요약용 유효 리뷰 필터(최소 10자 이상) 쿼리를 제공한다.
 */

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
	Page<Review> findByConcertConcertId(Long concertId, Pageable pageable);
	Optional<Review> findByIdAndConcertConcertId(Long id, Long concertId);
	// AI 요약을 위한 유효한 리뷰들을 조회
	// 1. 내용이 있는 리뷰 (content가 null이 아니고 빈 문자열이 아님)
	// 2. 최소 길이 조건 (10자 이상)
	@Query("""
        SELECT r FROM Review r 
        WHERE r.concert.id = :concertId 
        AND r.description IS NOT NULL 
        AND TRIM(r.description) != '' 
        AND LENGTH(TRIM(r.description)) >= 10
        ORDER BY r.createdAt DESC
        """)
		List<Review> findValidReviewsForAiSummary(@Param("concertId") Long concertId);

}