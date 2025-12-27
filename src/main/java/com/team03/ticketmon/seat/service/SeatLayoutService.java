package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.domain.enums.SeatGrade;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.dto.*;
import com.team03.ticketmon.seat.dto.GradePriceResponseDTO;
import com.team03.ticketmon.venue.dto.VenueDTO;
import com.team03.ticketmon.venue.service.VenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;

/**
 * 좌석 배치도 관련 비즈니스 로직 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatLayoutService {

    private final ConcertRepository concertRepository;
    private final ConcertSeatRepository concertSeatRepository;
    private final VenueService venueService;
    private final SeatStatusService seatStatusService;

    /**
     * 콘서트의 전체 좌석 배치도 조회
     *
     * @param concertId 콘서트 ID
     * @return 좌석 배치도 정보
     * @throws BusinessException 콘서트를 찾을 수 없는 경우
     */
    public SeatLayoutResponseDTO getSeatLayout(Long concertId) {
        log.info("좌석 배치도 조회 시작: concertId={}", concertId);

        try {
            // 1. 콘서트 존재 여부 확인
            Concert concert = concertRepository.findById(concertId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

            log.debug("콘서트 정보 조회 성공: concertId={}, title={}, venueName={}",
                    concertId, concert.getTitle(), concert.getVenueName());

            // 2. 공연장 정보 조회 (VenueName 으로 조회)
            VenueDTO venue = venueService.getVenueByName(concert.getVenueName());
            log.debug("공연장 정보 준비 완료: venueName={}", venue.getName());

            // venueInfo는 venue를 저장하는 게 아니라, venue 로부터 필요한 데이터만 추출해서 새로 만든 객체
            String capacityType = concert.getVenueCapacityType();
            SeatLayoutResponseDTO.VenueInfo venueInfo = SeatLayoutResponseDTO.VenueInfo.from(venue, capacityType);

            // 3. 콘서트의 모든 좌석 정보 조회 (Fetch Join 으로 최적화됨)
            // Fetch Join은 연관된 엔티티를 한 번의 쿼리로 같이 가져오는 JPA 의 최적화 기법 (N+1 문제 극복)
            List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

            if (concertSeats.isEmpty()) {
                log.warn("콘서트에 좌석 정보가 없습니다: concertId={}", concertId);
                // 빈 좌석 배치도 반환
                return SeatLayoutResponseDTO.from(concertId, venueInfo, List.of());
            }

            log.debug("좌석 정보 조회 성공: concertId={}, 총 좌석수={}", concertId, concertSeats.size());

            Map<Long, SeatStatus> seatStatuses = seatStatusService.getAllSeatStatus(concertId);

            List<SeatDetailResponseDTO> seatDetails = concertSeats.stream()
                    .map(cs -> {
                        SeatStatus status = seatStatuses.get(cs.getConcertSeatId());
                        boolean isAvailable = (status == null) ||
                                (status.getStatus() == SeatStatus.SeatStatusEnum.AVAILABLE);
                        return SeatDetailResponseDTO.from(cs, isAvailable);
                    })
                    .toList();

            Map<SeatGrade, List<SeatDetailResponseDTO>> seatsByGrade = seatDetails.stream()
                    .collect(Collectors.groupingBy(
                            SeatDetailResponseDTO::grade,
                            Collectors.toList()
                    ));

            log.debug("등급별 그룹핑 완료: concertId={}, 등급수={}, 등급={}",
                    concertId, seatsByGrade.size(), seatsByGrade.keySet());

            List<GradeLayoutResponseDTO> grades = seatsByGrade.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()) // 구역명으로 정렬 (A, B, C, VIP 등)
                    .map(entry -> GradeLayoutResponseDTO.from(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            SeatLayoutResponseDTO response = SeatLayoutResponseDTO.from(concertId, venueInfo, grades);

            log.info("좌석 배치도 조회 완료: concertId={}, 총좌석={}, 등급수={}",
                    concertId,
                    response.statistics().totalSeats(),
                    grades.size());

            return response;

        } catch (BusinessException e) {
            log.error("좌석 배치도 조회 중 비즈니스 예외: concertId={}, error={}", concertId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("좌석 배치도 조회 중 예상치 못한 오류: concertId={}", concertId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "좌석 배치도 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 특정 등급의 좌석 배치 조회
     * @param concertId 콘서트 ID
     * @param gradeName 등급명 (A, B, VIP 등)
     * @return 해당 등급의 좌석 배치 정보
     * @throws BusinessException 콘서트나 등급을 찾을 수 없는 경우
     */
    public GradeLayoutResponseDTO getGradeLayout(Long concertId, String gradeName) {
        log.info("등급별 좌석 배치도 조회: concertId={}, grade={}", concertId, gradeName);

        try {
            // 1. 콘서트 존재 여부 확인
            if (!concertRepository.existsById(concertId)) {
                log.warn("콘서트를 찾을 수 없음: concertId={}", concertId);
                throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
            }

            // 2. 입력값 검증
            if (gradeName == null || gradeName.trim().isEmpty()) {
                log.warn("등급명이 비어있음: concertId={}", concertId);
                throw new BusinessException(ErrorCode.INVALID_INPUT, "등급명을 입력해주세요.");
            }

            // 3. 문자열을 SeatGrade enum으로 변환
            SeatGrade targetGrade;
            try {
                targetGrade = SeatGrade.valueOf(gradeName.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "유효하지 않은 등급입니다: " + gradeName);
            }

            // 4. 해당 콘서트의 특정 등급의 모든 좌석을 DB 에서 가져옴
            List<ConcertSeat> concertSeats = concertSeatRepository
                    .findByConcertIdAndGrade(concertId, targetGrade);

            if (concertSeats.isEmpty()) {
                log.warn("해당 등급에 좌석이 없습니다: concertId={}, grade={}", concertId, concertSeats);

                // 사용자 친화적 에러 메시지 (사용 가능한 등급 목록 제공)
                List<String> availableGrades = concertSeats.stream()
                        .map(cs -> cs.getGrade().name())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());

                log.info("사용 가능한 등급 목록: concertId={}, grades={}", concertId, availableGrades);

                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND,
                        String.format("'%s'등급을 찾을 수 없습니다. 사용 가능한 등급: %s",
                                gradeName, String.join(", ", availableGrades)));
            }
            // 5. Redis에서 실시간 상태 조회
            Map<Long, SeatStatus> seatStatuses = seatStatusService.getAllSeatStatus(concertId);

            // 6. DB + Redis 합쳐서 DTO 변환
            List<SeatDetailResponseDTO> seatDetails = concertSeats.stream()
                    .map(cs -> {
                        SeatStatus status = seatStatuses.get(cs.getConcertSeatId());
                        boolean isAvailable = (status == null) ||
                                (status.getStatus() == SeatStatus.SeatStatusEnum.AVAILABLE);
                        return SeatDetailResponseDTO.from(cs, isAvailable);
                    })
                    .toList();

            return GradeLayoutResponseDTO.from(targetGrade, seatDetails);

        } catch (BusinessException e) {
            log.error("등급별 좌석 배치도 조회 중 비즈니스 예외: concertId={}, grade={}, error={}",
                    concertId, gradeName, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("등급별 좌석 배치도 조회 중 예상치 못한 오류: concertId={}, grade={}",
                    concertId, gradeName, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "등급별 좌석 배치도 조회 중 오류가 발생했습니다.");
        }
    }

    private static final String SEAT_COUNT_KEY_PREFIX = "seat:count:";

    @Autowired
    private RedissonClient redissonClient;  // 필드 추가

    /**
     * 등급별 좌석 카운트 조회 (빠른 조회용)
     */
    public List<GradeCountResponseDTO> getGradeCounts(Long concertId) {
        if (!concertRepository.existsById(concertId)) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
        }

        // 1. DB에서 등급별 총 좌석 수 조회
        List<Object[]> totalResults = concertSeatRepository.countSeatsByGrade(concertId);

        // 2. Redis에서 등급별 available 카운트 조회
        List<GradeCountResponseDTO> result = new ArrayList<>();

        for (Object[] row : totalResults) {
            SeatGrade grade = (SeatGrade) row[0];
            Long totalSeats = (Long) row[1];

            String countKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + grade.name() + ":available";
            long availableSeats = redissonClient.getAtomicLong(countKey).get();

            // 캐시 미스 시 (0이고 totalSeats > 0) → 전체가 available로 간주
            if (availableSeats == 0 && totalSeats > 0) {
                availableSeats = totalSeats;
            }

            result.add(new GradeCountResponseDTO(grade.name(), totalSeats, availableSeats));
        }

        return result;
    }

    public GradeLayoutResponseDTO getGradeSectionLayout(Long concertId, String gradeName, String sectionName) {
        log.info("등급 및 구역별 좌석 배치도 조회: concertId={}, grade={}, section={}", concertId, gradeName, sectionName);

        try {
            // 1. 콘서트 존재 여부 확인
            if (!concertRepository.existsById(concertId)) {
                log.warn("콘서트를 찾을 수 없음: concertId={}", concertId);
                throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
            }

            // 2. 입력값 검증
            if (gradeName == null || gradeName.trim().isEmpty()) {
                log.warn("등급명이 비어있음: concertId={}", concertId);
                throw new BusinessException(ErrorCode.INVALID_INPUT, "등급명을 입력해주세요.");
            }

            // 3. 문자열을 SeatGrade enum으로 변환
            SeatGrade targetGrade;
            try {
                targetGrade = SeatGrade.valueOf(gradeName.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "유효하지 않은 등급입니다: " + gradeName);
            }

            if (sectionName == null || sectionName.trim().isEmpty()) {
                log.warn("구역명이 비어있음: concertId={}", concertId);
                throw new BusinessException(ErrorCode.INVALID_INPUT, "구역명을 입력해주세요.");
            }

            // 4. 해당 콘서트의 특정 등급 및 구역의 모든 좌석을 DB 에서 가져옴
            List<ConcertSeat> concertSeats = concertSeatRepository
                    .findByConcertIdAndGradeAndSection(concertId, targetGrade, sectionName);

            if (concertSeats.isEmpty()) {
                log.warn("해당 등급 및 구역에 좌석이 없습니다: concertId={}, gradeSection={}", concertId, concertSeats);

                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND,"등급 또는 구역을 찾을 수 없습니다.");
            }

            // 5. Redis에서 실시간 상태 조회
            Map<Long, SeatStatus> seatStatuses = seatStatusService.getAllSeatStatus(concertId);

            // 6. DB + Redis 합쳐서 DTO 변환
            List<SeatDetailResponseDTO> seatDetails = concertSeats.stream()
                    .map(cs -> {
                        SeatStatus status = seatStatuses.get(cs.getConcertSeatId());
                        boolean isAvailable = (status == null) ||
                                (status.getStatus() == SeatStatus.SeatStatusEnum.AVAILABLE);
                        return SeatDetailResponseDTO.from(cs, isAvailable);
                    })
                    .toList();

            return GradeLayoutResponseDTO.from(targetGrade, seatDetails);

        } catch (BusinessException e) {
            log.error("등급별 좌석 배치도 조회 중 비즈니스 예외: concertId={}, grade={}, error={}",
                    concertId, gradeName, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("등급별 좌석 배치도 조회 중 예상치 못한 오류: concertId={}, grade={}",
                    concertId, gradeName, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "등급별 좌석 배치도 조회 중 오류가 발생했습니다.");
        }
    }

    public List<GradePriceResponseDTO> getGradePrices(Long concertId) {
        if (!concertRepository.existsById(concertId)) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
        }

        List<Object[]> results = concertSeatRepository.findGradePricesByConcertId(concertId);

        return results.stream()
                .map(row -> GradePriceResponseDTO.from(
                        (SeatGrade) row[0],
                        (BigDecimal) row[1]
                ))
                .toList();
    }

    /**
     * 특정 등급의 구역별 좌석 카운트 조회
     */
    public List<SectionCountResponseDTO> getSectionCounts(Long concertId, String gradeName) {
        if (!concertRepository.existsById(concertId)) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
        }

        // 1. 문자열을 SeatGrade enum으로 변환
        SeatGrade targetGrade;
        try {
            targetGrade = SeatGrade.valueOf(gradeName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 등급입니다: " + gradeName);
        }

        // 2. DB에서 구역별 총 좌석 수 조회
        List<Object[]> totalResults = concertSeatRepository.countSeatsByGradeAndSection(concertId, targetGrade);

        // 3. Redis에서 구역별 available 카운트 조회
        List<SectionCountResponseDTO> result = new ArrayList<>();

        for (Object[] row : totalResults) {
            String section = (String) row[0];
            Long totalSeats = (Long) row[1];

            String countKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + gradeName + ":" + section + ":available";
            long availableSeats = redissonClient.getAtomicLong(countKey).get();

            // 캐시 미스 시 totalSeats로 간주
            if (availableSeats == 0 && totalSeats > 0) {
                availableSeats = totalSeats;
            }

            result.add(new SectionCountResponseDTO(section, totalSeats, availableSeats));
        }

        return result;
    }
}