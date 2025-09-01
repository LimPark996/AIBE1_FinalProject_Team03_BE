package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.dto.SeatPriceResponse;
import com.team03.ticketmon.seat.dto.SeatStatusResponseDTO;
import com.team03.ticketmon.seat.service.SeatPriceService;
import com.team03.ticketmon.seat.service.SeatStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 좌석 상태 조회 컨트롤러 (읽기 전용)
 * - 콘서트 전체 좌석 상태 조회
 * - 개별 좌석 상태 조회
 * - 사용자 선점 좌석 조회
 */
@Tag(name = "좌석 상태 조회", description = "좌석 상태 읽기 전용 API")
@Slf4j
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatQueryController {

    private final SeatStatusService seatStatusService;
    private final SeatPriceService seatPriceService;

    @Operation(summary = "콘서트 전체 좌석 상태 조회", description = "특정 콘서트의 모든 좌석 상태를 조회합니다")
    @GetMapping("/concerts/{concertId}/status")
    public ResponseEntity<SuccessResponse<List<SeatStatusResponseDTO>>> getAllSeatStatus(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @AuthenticationPrincipal CustomUserDetails user) {

        Long userId = (user != null) ? user.getUserId() : null;


        try {
            Map<Long, SeatStatus> seatStatusMap = seatStatusService.getAllSeatStatus(concertId);

            List<SeatStatusResponseDTO> responses = seatStatusMap.values().stream()
                    .map(seat -> SeatStatusResponseDTO.from(seat, userId))
                    .collect(Collectors.toList());

            log.info("전체 좌석 상태 조회 성공: concertId={}, seatCount={}", concertId, responses.size());
            return ResponseEntity.ok(SuccessResponse.of("좌석 상태 조회 성공", responses));

        } catch (Exception e) {
            log.error("전체 좌석 상태 조회 중 오류 발생: concertId={}", concertId, e);

            // Redis 연결 오류 등 인프라 문제인지 확인
            if (e.getCause() != null && e.getCause().getMessage().contains("Redis")) {
                return ResponseEntity.status(503) // Service Unavailable
                        .body(SuccessResponse.of("일시적인 서비스 장애입니다. 잠시 후 다시 시도해주세요.", null));
            }

            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 상태 조회 중 오류가 발생했습니다.", null));
        }
    }

    @Operation(summary = "특정 좌석 상태 조회", description = "특정 좌석의 상태를 조회합니다")
    @GetMapping("/concerts/{concertId}/seats/{seatId}/status")
    public ResponseEntity<SuccessResponse<SeatStatusResponseDTO>> getSeatStatus(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId,
            @AuthenticationPrincipal CustomUserDetails user) {

        // 1. 현재 로그인한 사용자의 ID를 가져옵니다. 비로그인 상태일 수 있으므로 null 체크가 필요합니다.
        Long userId = (user != null) ? user.getUserId() : null;


        try {
            Optional<SeatStatus> seatStatus = seatStatusService.getSeatStatus(concertId, seatId);

            if (seatStatus.isPresent()) {
                SeatStatusResponseDTO response = SeatStatusResponseDTO.from(seatStatus.get(), userId);
                return ResponseEntity.ok(SuccessResponse.of("좌석 상태 조회 성공", response));
            } else {
                return ResponseEntity.ok(SuccessResponse.of("좌석 정보 없음", null));
            }

        } catch (Exception e) {
            log.error("좌석 상태 조회 중 오류 발생: concertId={}, seatId={}", concertId, seatId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 상태 조회 중 오류가 발생했습니다.", null));
        }
    }

    @Operation(summary = "사용자 선점 좌석 조회", description = "특정 사용자가 선점한 좌석들을 조회합니다")
    @GetMapping("/concerts/{concertId}/users/{userId}/reserved")
    public ResponseEntity<SuccessResponse<List<SeatStatusResponseDTO>>> getUserReservedSeats(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable Long userId) {

        try {
            List<SeatStatus> reservedSeats = seatStatusService.getUserReservedSeats(concertId, userId);

            List<SeatStatusResponseDTO> responses = reservedSeats.stream()
                    .map(seat -> SeatStatusResponseDTO.from(seat, userId))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(SuccessResponse.of("사용자 선점 좌석 조회 성공", responses));

        } catch (Exception e) {
            log.error("사용자 선점 좌석 조회 중 오류: concertId={}, userId={}", concertId, userId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("사용자 선점 좌석 조회 중 오류가 발생했습니다.", null));
        }
    }
    @Operation(summary = "콘서트 전체 좌석 가격 조회", description = "특정 콘서트의 모든 좌석 가격 정보를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 콘서트"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/concerts/{concertId}/prices")
    public ResponseEntity<SuccessResponse<List<SeatPriceResponse>>> getConcertSeatPrices(
            @Parameter(description = "콘서트 ID", required = true)
            @PathVariable Long concertId) {

        log.info("콘서트 좌석 가격 정보 조회 요청: concertId={}", concertId);

        try {
            List<SeatPriceResponse> seatPrices = seatPriceService.getConcertSeatPrices(concertId);

            log.info("콘서트 좌석 가격 조회 성공: concertId={}, seatCount={}", concertId, seatPrices.size());
            return ResponseEntity.ok(SuccessResponse.of("좌석 가격 정보 조회가 완료되었습니다.", seatPrices));

        } catch (Exception e) {
            log.error("콘서트 좌석 가격 조회 중 오류 발생: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 가격 조회 중 오류가 발생했습니다.", null));
        }
    }

    @Operation(summary = "선택된 좌석 가격 조회", description = "선택된 좌석들의 가격 정보를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (좌석 ID 누락 등)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 콘서트 또는 좌석"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/concerts/{concertId}/prices/selected")
    public ResponseEntity<SuccessResponse<List<SeatPriceResponse>>> getSelectedSeatPrices(
            @Parameter(description = "콘서트 ID", required = true)
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID 목록", required = true)
            @RequestParam List<Long> seatIds) {

        log.info("선택된 좌석 가격 정보 조회 요청: concertId={}, seatIds={}", concertId, seatIds);

        if (seatIds == null || seatIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(SuccessResponse.of("좌석 ID 목록이 비어있습니다.", null));
        }

        try {
            List<SeatPriceResponse> seatPrices = seatPriceService.getSelectedSeatPrices(concertId, seatIds);

            log.info("선택된 좌석 가격 조회 성공: concertId={}, requestedSeats={}, foundSeats={}",
                    concertId, seatIds.size(), seatPrices.size());
            return ResponseEntity.ok(SuccessResponse.of("선택된 좌석 가격 정보 조회가 완료되었습니다.", seatPrices));

        } catch (Exception e) {
            log.error("선택된 좌석 가격 조회 중 오류 발생: concertId={}, seatIds={}", concertId, seatIds, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 가격 조회 중 오류가 발생했습니다.", null));
        }
    }
}