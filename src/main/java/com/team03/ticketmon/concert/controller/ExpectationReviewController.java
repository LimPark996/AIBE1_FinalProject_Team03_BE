package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon.concert.dto.ExpectationReviewDTO;
import com.team03.ticketmon.concert.service.ExpectationReviewService;
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
 * ExpectationReviewController — 콘서트 기대평(관람 전) REST API
 *
 * 이 클래스가 하는 일:
 *   1. 콘서트별 기대평 목록 페이징 조회 (최신순 고정 정렬)
 *   2. 기대평 작성/수정/삭제 엔드포인트 제공
 *
 * 동작 흐름:
 *   - 요청 수신 → PathVariable 검증 → ExpectationReviewService 위임
 *     → Optional 결과를 HTTP 상태와 함께 SuccessResponse로 반환
 */
@Tag(name = "콘서트 기대평 API", description = "콘서트 기대평 작성, 수정, 삭제, 조회 관련 API")
@RestController
@RequestMapping("/api/concerts/{concertId}/expectations")
@RequiredArgsConstructor
@Validated
public class ExpectationReviewController {

	private final ExpectationReviewService expectationReviewService;

	@Operation(
		summary = "콘서트 기대평 목록 조회",
		description = """
		특정 콘서트의 기대평 목록을 페이징으로 조회합니다.
		작성일시 기준 내림차순으로 정렬됩니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "기대평 목록 조회 성공",
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
									"userNickname": "콘서트기대자",
									"comment": "아이유 콘서트 너무 기대돼요! 특히 '밤편지'를 라이브로 들을 수 있다니 정말 설레네요.",
									"expectationRating": 5,
									"createdAt": "2025-08-16T10:30:00",
									"updatedAt": "2025-08-16T10:30:00"
								}
							],
							"totalElements": 25,
							"totalPages": 3,
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
	@GetMapping
	public ResponseEntity<SuccessResponse<Page<ExpectationReviewDTO>>> getConcertExpectationReviews(
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
		@RequestParam(defaultValue = "10") int size
	) {
		// 고정: 최신순 정렬
		Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

		Page<ExpectationReviewDTO> expectations =
			expectationReviewService.getConcertExpectationReviews(concertId, pageable);

		return ResponseEntity.ok(SuccessResponse.of(expectations));
	}

	@Operation(
		summary = "콘서트 기대평 작성",
		description = """
		콘서트 관람 전 기대평을 작성합니다.
		기대 점수는 1-5점 사이의 정수로 입력해야 합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = "기대평 작성 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "작성 성공 응답",
					value = """
					{
						"success": true,
						"message": "기대평이 작성되었습니다.",
						"data": {
							"id": 1,
							"concertId": 100,
							"userId": 200,
							"userNickname": "콘서트기대자",
							"comment": "아이유 콘서트 너무 기대돼요! 특히 '밤편지'를 라이브로 들을 수 있다니 정말 설레네요.",
							"expectationRating": 5,
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
							"message": "기대평 내용은 필수입니다",
							"data": null
						}
						"""
					),
					@ExampleObject(
						name = "기대 점수 범위 오류",
						value = """
						{
							"success": false,
							"message": "기대 점수는 1 이상 5 이하여야 합니다",
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
	@PostMapping
	public ResponseEntity<SuccessResponse<ExpectationReviewDTO>> createExpectationReview(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId,

		@Parameter(
			description = "**기대평 작성 정보**",
			required = true
		)
		@Valid @RequestBody ExpectationReviewDTO expectationDTO
	) {
		expectationDTO.setConcertId(concertId);
		ExpectationReviewDTO created = expectationReviewService.createExpectationReview(expectationDTO);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(SuccessResponse.of("기대평이 작성되었습니다.", created));
	}

	@Operation(
		summary = "콘서트 기대평 수정",
		description = """
		작성한 기대평의 내용과 기대 점수를 수정합니다.
		본인이 작성한 기대평만 수정 가능합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "기대평 수정 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "수정 성공 응답",
					value = """
					{
						"success": true,
						"message": "기대평이 수정되었습니다.",
						"data": {
							"id": 1,
							"concertId": 100,
							"userId": 200,
							"userNickname": "콘서트기대자",
							"comment": "수정된 기대평 내용입니다. 더욱 기대가 됩니다!",
							"expectationRating": 4,
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
						"message": "기대평 내용은 필수입니다",
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
			description = "기대평을 찾을 수 없음",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "기대평을 찾을 수 없습니다",
						"data": null
					}
					"""
				)
			)
		)
	})
	@PutMapping("/{expectationId}")
	public ResponseEntity<SuccessResponse<ExpectationReviewDTO>> updateExpectationReview(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId,

		@Parameter(
			description = "**기대평 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long expectationId,

		@Parameter(
			description = "**기대평 수정 정보**",
			required = true
		)
		@Valid @RequestBody ExpectationReviewDTO expectationDTO
	) {
		return expectationReviewService.updateExpectationReview(concertId, expectationId, expectationDTO)
			.map(review -> ResponseEntity.ok(SuccessResponse.of("기대평이 수정되었습니다.", review)))
			.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(SuccessResponse.of("기대평을 찾을 수 없습니다.", null)));
	}

	@Operation(
		summary = "콘서트 기대평 삭제",
		description = """
		작성한 기대평을 삭제합니다.
		본인이 작성한 기대평만 삭제 가능합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "기대평 삭제 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "삭제 성공 응답",
					value = """
					{
						"success": true,
						"message": "기대평이 삭제되었습니다.",
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
			description = "기대평을 찾을 수 없음",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "기대평을 찾을 수 없습니다.",
						"data": null
					}
					"""
				)
			)
		)
	})
	@DeleteMapping("/{expectationId}")
	public ResponseEntity<SuccessResponse<Void>> deleteExpectationReview(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId,

		@Parameter(
			description = "**기대평 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long expectationId
	) {
		boolean deleted = expectationReviewService.deleteExpectationReview(concertId, expectationId);
		return deleted ?
			ResponseEntity.ok(SuccessResponse.of("기대평이 삭제되었습니다.", null)) :
			ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(SuccessResponse.of("기대평을 찾을 수 없습니다.", null));
	}
}