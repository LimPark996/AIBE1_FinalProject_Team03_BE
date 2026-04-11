package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.dto.ReviewDTO;
import com.team03.ticketmon.concert.service.ReviewService;
import com.team03.ticketmon._global.exception.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * ReviewController — 콘서트 관람평(후기) REST API
 *
 * 이 클래스가 하는 일:
 *   1. 콘서트별 후기 목록 페이징 조회 (정렬 필드/방향 지정 가능)
 *   2. 후기 작성/수정/삭제 엔드포인트 제공
 *   3. 단일 후기 상세 조회 (concertId + reviewId 조합 검증)
 *
 * 동작 흐름:
 *   - 요청 수신 → PathVariable 검증(@Min) → ReviewService 위임
 *     → Optional 결과를 ResponseEntity로 매핑 → SuccessResponse 반환
 *   - 작성 시 요청 본문의 concertId와 PathVariable concertId의 일치 여부를 확인한다.
 */
@Tag(name = "콘서트 관람평 API", description = "콘서트 후기 작성, 수정, 삭제, 조회 관련 API")
@RestController
@RequestMapping("/api/concerts")
@RequiredArgsConstructor
@Validated
public class ReviewController {

	private final ReviewService reviewService;

	@Operation(
		summary = "콘서트 후기 목록 조회",
		description = """
		특정 콘서트의 후기 목록을 페이징으로 조회합니다.
		작성일시 기준 내림차순으로 정렬됩니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "후기 목록 조회 성공",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = SuccessResponse.class),
				examples = @ExampleObject(
					name = "성공 응답 예시",
					value = """
					{
						"success": true,
						"message": "성공",
						"data": {
							"content": [
								{
									"id": 1,
									"concertId": 100,
									"userId": 200,
									"userNickname": "콘서트러버",
									"title": "정말 감동적인 콘서트였어요!",
									"description": "아이유의 라이브 실력이 정말 대단했습니다. 2시간 30분 동안 한 순간도 지루하지 않았어요.",
									"rating": 5,
									"createdAt": "2025-08-16T10:30:00",
									"updatedAt": "2025-08-16T10:30:00"
								}
							],
							"totalElements": 50,
							"totalPages": 5,
							"size": 10,
							"number": 0,
							"first": true,
							"last": false
						}
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "404",
			description = "콘서트를 찾을 수 없음",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "콘서트를 찾을 수 없습니다",
						"data": null
					}
					"""
				)
			)
		)
	})
	@GetMapping("/reviews/{concertId}")
	public ResponseEntity<SuccessResponse<Page<ReviewDTO>>> getConcertReviews(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId,

		@Parameter(
			description = "**페이지 번호** (0부터 시작)",
			example = "0",
			schema = @Schema(minimum = "0")
		)
		@RequestParam(defaultValue = "0") int page,

		@Parameter(
			description = "**페이지 크기** (한 페이지당 항목 수)",
			example = "10",
			schema = @Schema(minimum = "1", maximum = "100")
		)
		@RequestParam(defaultValue = "10") int size,

		@Parameter(
			description = "**정렬 기준** (createdAt, rating, title 등)",
			example = "createdAt",
			schema = @Schema(allowableValues = {"createdAt", "rating", "title"})
		)
		@RequestParam(defaultValue = "createdAt") String sortBy,

		@Parameter(
			description = "**정렬 방향** (asc: 오름차순, desc: 내림차순)",
			example = "desc",
			schema = @Schema(allowableValues = {"asc", "desc"})
		)
		@RequestParam(defaultValue = "desc") String sortDir
	) {
		// 안전한 Sort 객체 생성
		Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
			? Sort.Direction.ASC
			: Sort.Direction.DESC;

		Sort sort = Sort.by(direction, sortBy);
		Pageable pageable = PageRequest.of(page, size, sort);

		Page<ReviewDTO> reviews = reviewService.getConcertReviews(concertId, pageable);
		return ResponseEntity.ok(SuccessResponse.of(reviews));
	}

	@Operation(
		summary = "콘서트 후기 작성",
		description = """
		콘서트 관람 후 후기를 작성합니다.
		평점은 1-5점 사이의 정수로 입력해야 합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = "후기 작성 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "작성 성공 응답",
					value = """
					{
						"success": true,
						"message": "후기가 작성되었습니다.",
						"data": {
							"id": 1,
							"concertId": 100,
							"userId": 200,
							"userNickname": "콘서트러버",
							"title": "정말 감동적인 콘서트였어요!",
							"description": "아이유의 라이브 실력이 정말 대단했습니다. 2시간 30분 동안 한 순간도 지루하지 않았어요.",
							"rating": 5,
							"createdAt": "2025-08-16T10:30:00",
							"updatedAt": "2025-08-16T10:30:00"
						}
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 입력값",
			content = @Content(
				examples = {
					@ExampleObject(
						name = "필수 필드 누락",
						value = """
						{
							"success": false,
							"message": "후기 제목은 필수입니다",
							"data": null
						}
						"""
					),
					@ExampleObject(
						name = "평점 범위 오류",
						value = """
						{
							"success": false,
							"message": "평점은 1 이상 5 이하여야 합니다",
							"data": null
						}
						"""
					),
					@ExampleObject(
						name = "콘서트 ID 불일치",
						value = """
						{
							"success": false,
							"message": "유효하지 않은 입력값입니다",
							"data": null
						}
						"""
					)
				}
			)
		),
		@ApiResponse(
			responseCode = "404",
			description = "콘서트를 찾을 수 없음"
		)
	})
	@PostMapping("/{concertId}/reviews")
	public ResponseEntity<SuccessResponse<ReviewDTO>> createReview(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId,

		@Parameter(
			description = "**후기 작성 정보**",
			required = true
		)
		@RequestBody @Valid ReviewDTO reviewDTO) {

		// concertId 일치 여부 검증
		if (reviewDTO.getConcertId() != null && !reviewDTO.getConcertId().equals(concertId)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		reviewDTO.setConcertId(concertId);

		ReviewDTO createdReview = reviewService.createReview(reviewDTO);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(SuccessResponse.of("후기가 작성되었습니다.", createdReview));
	}

	@Operation(
		summary = "콘서트 후기 수정",
		description = """
		작성한 후기의 제목과 내용을 수정합니다.
		본인이 작성한 후기만 수정 가능합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "후기 수정 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "수정 성공 응답",
					value = """
					{
						"success": true,
						"message": "후기가 수정되었습니다.",
						"data": {
							"id": 1,
							"concertId": 100,
							"userId": 200,
							"userNickname": "콘서트러버",
							"title": "수정된 후기 제목",
							"description": "수정된 후기 내용입니다.",
							"rating": 5,
							"createdAt": "2025-08-16T10:30:00",
							"updatedAt": "2025-08-16T15:45:00"
						}
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 입력값",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "후기 제목은 필수입니다",
						"data": null
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "403",
			description = "수정 권한 없음"
		),
		@ApiResponse(
			responseCode = "404",
			description = "후기를 찾을 수 없음",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "후기를 찾을 수 없습니다",
						"data": null
					}
					"""
				)
			)
		)
	})
	@PutMapping("/{concertId}/reviews/{reviewId}")
	public ResponseEntity<SuccessResponse<ReviewDTO>> updateReview(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId,

		@Parameter(
			description = "**후기 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long reviewId,

		@Parameter(
			description = "**후기 수정 정보**",
			required = true
		)
		@RequestBody @Valid ReviewDTO reviewDTO) {

		return reviewService.updateReview(reviewId, concertId, reviewDTO)
			.map(review -> ResponseEntity.ok(SuccessResponse.of("후기가 수정되었습니다.", review)))
			.orElse(ResponseEntity.notFound().build());
	}

	@Operation(
		summary = "콘서트 후기 삭제",
		description = """
		작성한 후기를 삭제합니다.
		본인이 작성한 후기만 삭제 가능합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "후기 삭제 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "삭제 성공 응답",
					value = """
					{
						"success": true,
						"message": "후기가 삭제되었습니다.",
						"data": null
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "403",
			description = "삭제 권한 없음"
		),
		@ApiResponse(
			responseCode = "404",
			description = "후기를 찾을 수 없음",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "후기를 찾을 수 없습니다",
						"data": null
					}
					"""
				)
			)
		)
	})
	@DeleteMapping("/{concertId}/reviews/{reviewId}")
	public ResponseEntity<SuccessResponse<Void>> deleteReview(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId,

		@Parameter(
			description = "**후기 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long reviewId) {

		boolean deleted = reviewService.deleteReview(reviewId, concertId);
		return deleted
			? ResponseEntity.ok(SuccessResponse.of("후기가 삭제되었습니다.", null))
			: ResponseEntity.notFound().build();
	}
	@Operation(
		summary = "콘서트 후기 상세 조회",
		description = """
		특정 콘서트의 특정 후기를 상세 조회합니다.
		후기 ID와 콘서트 ID가 모두 일치해야 조회됩니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "후기 상세 조회 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "상세 조회 성공 응답",
					value = """
					{
						"success": true,
						"message": "성공",
						"data": {
							"id": 1,
							"concertId": 100,
							"userId": 200,
							"userNickname": "콘서트러버",
							"title": "정말 감동적인 콘서트였어요!",
							"description": "아이유의 라이브 실력이 정말 대단했습니다. 2시간 30분 동안 한 순간도 지루하지 않았어요. 특히 '좋은 날' 라이브는 정말 소름 돋았습니다! 무대 연출도 화려하고, 관객과의 소통도 자연스러웠어요. 다음 콘서트도 꼭 가고 싶습니다.",
							"rating": 5,
							"createdAt": "2025-08-16T10:30:00",
							"updatedAt": "2025-08-16T10:30:00"
						}
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 ID 값",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "콘서트 ID는 1 이상의 양수여야 합니다",
						"data": null
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "404",
			description = "후기를 찾을 수 없음",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "후기를 찾을 수 없습니다",
						"data": null
					}
					"""
				)
			)
		)
	})
	@GetMapping("/{concertId}/reviews/{reviewId}")
	public ResponseEntity<SuccessResponse<ReviewDTO>> getReviewDetail(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId,

		@Parameter(
			description = "**후기 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long reviewId) {

		return reviewService.getReviewDetail(concertId, reviewId)
			.map(review -> ResponseEntity.ok(SuccessResponse.of(review)))
			.orElse(ResponseEntity.notFound().build());
	}
}