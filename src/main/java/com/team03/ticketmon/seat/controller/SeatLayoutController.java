package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.seat.dto.*;
import com.team03.ticketmon.seat.dto.GradePriceResponseDTO;
import com.team03.ticketmon.seat.service.SeatLayoutService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 좌석 배치도 조회 컨트롤러
 * 실제 DB 데이터를 기반으로 한 좌석 배치도 정보를 제공하는 API
 */
@Tag(name = "좌석 배치도", description = "실제 DB 기반 좌석 배치도 조회 API")
@Slf4j
@RestController
@RequestMapping("/api/concerts")
@RequiredArgsConstructor
public class SeatLayoutController {

    private final SeatLayoutService seatLayoutService;
    private final ConcertRepository concertRepository;
    private final RedissonClient redissonClient;

    private static final String SEAT_COUNT_KEY_PREFIX = "seat:count:";
    private static final String SEAT_STATUS_KEY_PREFIX = "seat:status:";
    /**
     * 콘서트 전체 좌석 배치도 조회
     * 실제 DB 데이터를 기반으로 좌석 정보, 가격, 예매 상태를 제공
     */
    @GetMapping("/{concertId}/seat-layout")
    public ResponseEntity<SuccessResponse<SeatLayoutResponseDTO>> getSeatLayout(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            log.info("좌석 배치도 조회 요청: concertId={}", concertId);

            SeatLayoutResponseDTO seatLayout = seatLayoutService.getSeatLayout(concertId);

            log.info("좌석 배치도 조회 성공: concertId={}, 총좌석={}, 구역수={}",
                    concertId,
                    seatLayout.statistics().totalSeats(),
                    seatLayout.grades().size());

            return ResponseEntity.ok(SuccessResponse.of("좌석 배치도 조회 성공", seatLayout));

        } catch (Exception e) {
            log.error("좌석 배치도 조회 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 배치도 조회 중 오류가 발생했습니다", null));
        }
    }

    @GetMapping("/{concertId}/grade-prices")
    public ResponseEntity<SuccessResponse<List<GradePriceResponseDTO>>> getGradePrices(
            @PathVariable Long concertId) {

        List<GradePriceResponseDTO> prices = seatLayoutService.getGradePrices(concertId);
        return ResponseEntity.ok(SuccessResponse.of("등급별 가격 조회 성공", prices));
    }

    /**
     * 특정 구역의 좌석 배치 조회
     * 구역별 상세 정보가 필요한 경우 사용
     */
    @GetMapping("/{concertId}/seat-layout/grades/{gradeName}")
    public ResponseEntity<SuccessResponse<GradeLayoutResponseDTO>> getGradeLayout(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "등급명", example = "A")
            @PathVariable String gradeName) {

        try {
            log.info("등급별 좌석 배치도 조회 요청: concertId={}, grade={}", concertId, gradeName);

            GradeLayoutResponseDTO gradeLayout = seatLayoutService.getGradeLayout(concertId, gradeName);

            log.info("등급별 좌석 배치도 조회 성공: concertId={}, grade={}, 좌석수={}",
                    concertId, gradeName, gradeLayout.totalSeats());

            return ResponseEntity.ok(SuccessResponse.of("등급별 좌석 배치도 조회 성공", gradeLayout));

        } catch (Exception e) {
            log.error("등급별 좌석 배치도 조회 중 오류: concertId={}, grade={}", concertId, gradeName, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("등급별 좌석 배치도 조회 중 오류가 발생했습니다", null));
        }
    }

    @GetMapping("/{concertId}/grade-counts")
    public ResponseEntity<SuccessResponse<List<GradeCountResponseDTO>>> getGradeCounts(
            @PathVariable Long concertId) {

        log.info("등급별 좌석 카운트 조회: concertId={}", concertId);
        List<GradeCountResponseDTO> counts = seatLayoutService.getGradeCounts(concertId);
        return ResponseEntity.ok(SuccessResponse.of("등급별 좌석 카운트 조회 성공", counts));
    }

    @GetMapping("/{concertId}/seat-layout/grades/{gradeName}/sections/{sectionName}")
    public ResponseEntity<SuccessResponse<GradeLayoutResponseDTO>> getGradeSectionLayout(
            @PathVariable Long concertId,
            @PathVariable String gradeName,
            @PathVariable String sectionName) {

        try {
            log.info("등급+구역별 좌석 배치도 조회: concertId={}, grade={}, section={}",
                    concertId, gradeName, sectionName);

            GradeLayoutResponseDTO layout = seatLayoutService
                    .getGradeSectionLayout(concertId, gradeName, sectionName);

            return ResponseEntity.ok(SuccessResponse.of("등급+구역별 좌석 조회 성공", layout));

        } catch (Exception e) {
            log.error("등급+구역별 좌석 조회 오류: concertId={}, grade={}, section={}",
                    concertId, gradeName, sectionName, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 조회 중 오류가 발생했습니다", null));
        }
    }

    @GetMapping("/{concertId}/grades/{gradeName}/section-counts")
    public ResponseEntity<SuccessResponse<List<SectionCountResponseDTO>>> getSectionCounts(
            @PathVariable Long concertId,
            @PathVariable String gradeName) {

        log.info("구역별 좌석 카운트 조회: concertId={}, grade={}", concertId, gradeName);
        List<SectionCountResponseDTO> counts = seatLayoutService.getSectionCounts(concertId, gradeName);
        return ResponseEntity.ok(SuccessResponse.of("구역별 좌석 카운트 조회 성공", counts));
    }
}