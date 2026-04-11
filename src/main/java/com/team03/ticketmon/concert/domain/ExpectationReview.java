package com.team03.ticketmon.concert.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.team03.ticketmon._global.entity.BaseTimeEntity;

/**
 * ExpectationReview — 콘서트 관람 전 기대평 엔티티
 * Concert와 ManyToOne으로 연결되며 1~5점 사이의 기대 점수를 보유한다.
 */

@Entity
@Getter
@Setter
@Table(name = "expectationReviews")
@ToString(exclude = {"concert"})
@EqualsAndHashCode(of = "id", callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class ExpectationReview extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concert_id", nullable = false)
	private Concert concert;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "user_nickname", nullable = false)
	private String userNickname;

	@Column(columnDefinition = "TEXT")
	private String comment;

	// 1-5 기대 점수
	@Min(value = 1, message = "기대 점수는 1 이상이어야 합니다")
	@Max(value = 5, message = "기대 점수는 5 이하여야 합니다")
	@Column(name = "expectation_rating", nullable = false)
	private Integer expectationRating;
}
