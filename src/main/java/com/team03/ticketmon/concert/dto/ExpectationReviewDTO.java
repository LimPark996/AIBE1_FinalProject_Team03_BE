package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * ExpectationReviewDTO — 콘서트 기대평 전송 객체 (요청/응답 공용)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "콘서트 기대평 정보")
public class ExpectationReviewDTO {

	@Schema(description = "기대평 ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
	private Long id;

	@Schema(description = "콘서트 ID", example = "100", accessMode = Schema.AccessMode.READ_ONLY)
	private Long concertId;

	@NotNull(message = "사용자 ID는 필수입니다")
	@Schema(description = "작성자 사용자 ID", example = "200", minimum = "1")
	private Long userId;

	@NotBlank(message = "사용자 닉네임은 필수입니다")
	@Size(max = 50, message = "사용자 닉네임은 50자 이하여야 합니다")
	@Schema(description = "작성자 닉네임", example = "콘서트기대자", maxLength = 50)
	private String userNickname;

	@NotBlank(message = "기대평 내용은 필수입니다")
	@Size(max = 500, message = "기대평 내용은 500자 이하여야 합니다")
	@Schema(description = "기대평 내용",
		example = "아이유 콘서트 너무 기대돼요! 특히 '밤편지'를 라이브로 들을 수 있다니 정말 설레네요. 무대 연출도 어떨지 궁금합니다.",
		maxLength = 500)
	private String comment;

	@NotNull(message = "기대 점수는 필수입니다")
	@Min(value = 1, message = "기대 점수는 1 이상이어야 합니다")
	@Max(value = 5, message = "기대 점수는 5 이하여야 합니다")
	@Schema(description = "기대 점수 (1-5점)", example = "5", minimum = "1", maximum = "5")
	private Integer expectationRating;

	@Schema(description = "기대평 작성일시", example = "2025-08-16T10:30:00", accessMode = Schema.AccessMode.READ_ONLY)
	private LocalDateTime createdAt;

	@Schema(description = "기대평 수정일시", example = "2025-08-16T15:45:00", accessMode = Schema.AccessMode.READ_ONLY)
	private LocalDateTime updatedAt;
}