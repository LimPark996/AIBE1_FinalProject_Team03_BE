package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.seat.dto.SeatLayoutResponseDTO;
import com.team03.ticketmon.seat.dto.GradeLayoutResponseDTO;
import com.team03.ticketmon.seat.service.SeatLayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 콘서트 전체 좌석 배치도 조회
     * 실제 DB 데이터를 기반으로 좌석 정보, 가격, 예매 상태를 제공
     */
    @Operation(
            summary = "콘서트 좌석 배치도 조회",
            description = """
            콘서트의 전체 좌석 배치도를 조회합니다.
            
            **제공 정보:**
            - 공연장 정보 (이름, 수용 인원)
            - 구역별 좌석 배치 (A구역, B구역, VIP 등)
            - 좌석별 상세 정보 (위치, 가격, 등급, 예매 상태)
            - 실시간 예매 가능 여부
            - 가격 범위 및 통계 정보
            
            **실제 DB 연동:**
            - Venue, Seat, ConcertSeat 엔티티 기반
            - 예매 완료된 좌석은 isAvailable=false로 표시
            - Fetch Join으로 성능 최적화
            """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "좌석 배치도 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "좌석 배치도 응답 예시",
                                    value = """
                                    {
                                        "success": true,
                                        "message": "좌석 배치도 조회 성공",
                                        "data": {
                                            "concertId": 1,
                                            "venueInfo": {
                                                "venueId": 1,
                                                "venueName": "올림픽공원 체조경기장"
                                            },
                                            "statistics": {
                                                "totalSeats": 1000,
                                                "availableSeats": 856,
                                                "bookedSeats": 144,
                                                "availabilityRate": 85.6,
                                                "priceRange": {
                                                    "minPrice": 50000,
                                                    "maxPrice": 200000
                                                }
                                            },
                                            "sections": [
                                                {
                                                    "sectionName": "A",
                                                    "sectionDescription": "A석",
                                                    "totalSeats": 200,
                                                    "availableSeats": 180,
                                                    "priceRange": {
                                                        "minPrice": 100000,
                                                        "maxPrice": 120000
                                                    },
                                                    "seats": [
                                                        {
                                                            "seatId": 1,
                                                            "section": "A",
                                                            "seatRow": "1",
                                                            "seatNumber": 1,
                                                            "seatLabel": "A-1-1",
                                                            "grade": "일반",
                                                            "price": 100000,
                                                            "isAvailable": true
                                                        }
                                                    ]
                                                }
                                            ],
                                            "retrievedAt": "2025-06-25T14:30:00"
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
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                    {
                                        "success": false,
                                        "message": "좌석 배치도 조회 중 오류가 발생했습니다",
                                        "data": null
                                    }
                                    """
                            )
                    )
            )
    })
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

    /**
     * 특정 구역의 좌석 배치 조회
     * 구역별 상세 정보가 필요한 경우 사용
     */
    @Operation(
            summary = "구역별 좌석 배치도 조회",
            description = """
            특정 구역(A, B, VIP 등)의 상세 좌석 배치를 조회합니다.
            
            **사용 사례:**
            - 특정 구역만 확대해서 보여주고 싶을 때
            - 구역별 필터링된 좌석 정보가 필요할 때
            - 프론트엔드에서 구역별 렌더링을 할 때
            
            **제공 정보:**
            - 해당 구역의 모든 좌석 정보
            - 열별 좌석 그룹핑 (렌더링 최적화)
            - 구역별 가격 범위 및 예매 현황
            """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "구역별 좌석 배치도 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "A구역 좌석 배치도",
                                    value = """
                                    {
                                        "success": true,
                                        "message": "구역별 좌석 배치도 조회 성공",
                                        "data": {
                                            "sectionName": "A",
                                            "sectionDescription": "A석",
                                            "totalSeats": 200,
                                            "availableSeats": 180,
                                            "priceRange": {
                                                "minPrice": 100000,
                                                "maxPrice": 120000
                                            },
                                            "seats": [
                                                {
                                                    "seatId": 1,
                                                    "section": "A",
                                                    "seatRow": "1",
                                                    "seatNumber": 1,
                                                    "seatLabel": "A-1-1",
                                                    "grade": "일반",
                                                    "price": 100000,
                                                    "isAvailable": true
                                                }
                                            ],
                                            "seatsByRow": {
                                                "1": [
                                                    {
                                                        "seatId": 1,
                                                        "section": "A",
                                                        "seatRow": "1",
                                                        "seatNumber": 1,
                                                        "seatLabel": "A-1-1",
                                                        "grade": "일반",
                                                        "price": 100000,
                                                        "isAvailable": true
                                                    }
                                                ]
                                            }
                                        }
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "콘서트나 구역을 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                    {
                                        "success": false,
                                        "message": "해당 구역을 찾을 수 없습니다",
                                        "data": null
                                    }
                                    """
                            )
                    )
            )
    })
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

    /**
     * 좌석 배치도 요약 정보만 조회 (경량화된 API)
     * 프론트엔드에서 개요만 필요한 경우 사용
     */
    @Operation(
            summary = "좌석 배치도 요약 정보 조회",
            description = """
            좌석 배치도의 요약 정보만 조회합니다 (경량화된 응답).
            
            **사용 사례:**
            - 콘서트 목록에서 좌석 현황 미리보기
            - 모바일에서 빠른 로딩이 필요한 경우
            - 대시보드에서 통계 정보만 필요한 경우
            
            **제공 정보:**
            - 공연장 기본 정보
            - 전체 좌석 통계
            - 등급별 요약 (상세 좌석 정보 제외)
            """
    )
    @GetMapping("/{concertId}/seat-layout/summary")
    public ResponseEntity<SuccessResponse<SeatLayoutResponseDTO.SeatStatistics>> getSeatLayoutSummary(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            log.info("좌석 배치도 요약 조회 요청: concertId={}", concertId);

            SeatLayoutResponseDTO fullLayout = seatLayoutService.getSeatLayout(concertId);
            SeatLayoutResponseDTO.SeatStatistics summary = fullLayout.statistics();

            log.info("좌석 배치도 요약 조회 성공: concertId={}, 예매가능률={}%",
                    concertId, summary.availabilityRate());

            return ResponseEntity.ok(SuccessResponse.of("좌석 배치도 요약 조회 성공", summary));

        } catch (Exception e) {
            log.error("좌석 배치도 요약 조회 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 배치도 요약 조회 중 오류가 발생했습니다", null));
        }
    }
}