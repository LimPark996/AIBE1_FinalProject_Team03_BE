package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * ReviewDTO — 콘서트 관람 후기 전송 객체 (요청/응답 공용)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "콘서트 후기 정보")
public class ReviewDTO {

	@Schema(description = "후기 ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
	private Long id;

	@Schema(description = "콘서트 ID", example = "100", accessMode = Schema.AccessMode.READ_ONLY)
	private Long concertId;

	@NotNull(message = "사용자 ID는 필수입니다")
	@Schema(description = "작성자 사용자 ID", example = "200", minimum = "1")
	private Long userId;

	@NotBlank(message = "사용자 닉네임은 필수입니다")
	@Size(max = 50, message = "사용자 닉네임은 50자 이하여야 합니다")
	@Schema(description = "작성자 닉네임", example = "콘서트러버", maxLength = 50)
	private String userNickname;

	@NotBlank(message = "후기 제목은 필수입니다")
	@Size(max = 100, message = "후기 제목은 100자 이하여야 합니다")
	@Schema(description = "후기 제목", example = "정말 감동적인 콘서트였어요!", maxLength = 100)
	private String title;

	@NotBlank(message = "후기 내용은 필수입니다")
	@Size(max = 1000, message = "후기 내용은 1000자 이하여야 합니다")
	@Schema(description = "후기 내용",
		example = "아이유의 라이브 실력이 정말 대단했습니다. 2시간 30분 동안 한 순간도 지루하지 않았어요. 특히 '좋은 날' 라이브는 정말 소름 돋았습니다!",
		maxLength = 1000)
	private String description;

	@NotNull(message = "평점은 필수입니다")
	@Min(value = 1, message = "평점은 1 이상이어야 합니다")
	@Max(value = 5, message = "평점은 5 이하여야 합니다")
	@Schema(description = "관람 평점 (1-5점)", example = "5", minimum = "1", maximum = "5")
	private Integer rating;

	@Schema(description = "후기 작성일시", example = "2025-08-16T10:30:00", accessMode = Schema.AccessMode.READ_ONLY)
	private LocalDateTime createdAt;

	@Schema(description = "후기 수정일시", example = "2025-08-16T15:45:00", accessMode = Schema.AccessMode.READ_ONLY)
	private LocalDateTime updatedAt;
}