package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.seat.dto.SeatDetailResponseDTO;
import com.team03.ticketmon.seat.dto.SeatLayoutResponseDTO;
import com.team03.ticketmon.seat.dto.SectionLayoutResponseDTO;
import com.team03.ticketmon.venue.dto.VenueDTO;
import com.team03.ticketmon.venue.service.VenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // ConcertSeat: DB에 저장된 "이 콘서트의 이 좌석" 정보
    // SeatDetailResponseDTO: 클라이언트에게 보여줄 "좌석 1개"의 정보
    // SectionLayoutResponseDTO: "A구역" 전체의 통계와 좌석 목록
    // SeatLayoutResponseDTO: 콘서트 전체의 좌석 배치도

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
            if (venue == null) {
                    log.warn("공연장 정보를 찾을 수 없음: venueName={}, concertId={}", concert.getVenueName(), concertId);
                    log.info("대체 공연장 정보 사용: venueName={}", concert.getVenueName());
                    venue = createFallbackVenueInfo(concert.getVenueName());
            }

            log.debug("공연장 정보 준비 완료: venueName={}", venue.getName());

            // venueInfo는 venue를 저장하는 게 아니라, venue 로부터 필요한 데이터만 추출해서 새로 만든 객체
            SeatLayoutResponseDTO.VenueInfo venueInfo = SeatLayoutResponseDTO.VenueInfo.from(venue); // VenueDTO 에서 공연장 ID와 공연장명을 추출하여 저장한다.

            // 3. 콘서트의 모든 좌석 정보 조회 (Fetch Join 으로 최적화됨)
            // Fetch Join은 연관된 엔티티를 한 번의 쿼리로 같이 가져오는 JPA 의 최적화 기법 (N+1 문제 극복)
            List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

            if (concertSeats.isEmpty()) {
                log.warn("콘서트에 좌석 정보가 없습니다: concertId={}", concertId);
                // 빈 좌석 배치도 반환
                return SeatLayoutResponseDTO.from(concertId, venueInfo, List.of());
            }

            log.debug("좌석 정보 조회 성공: concertId={}, 총 좌석수={}", concertId, concertSeats.size());

            // 4. 좌석 정보를 DTO로 변환
            List<SeatDetailResponseDTO> seatDetails = concertSeats.stream()
                    .map(SeatDetailResponseDTO::from)
                    .toList();

            // 5. 구역별로 그룹핑
            Map<String, List<SeatDetailResponseDTO>> seatsBySection = seatDetails.stream()
                    .collect(Collectors.groupingBy(
                            SeatDetailResponseDTO::section,
                            Collectors.toList()
                    ));

            log.debug("구역별 그룹핑 완료: concertId={}, 구역수={}, 구역={}",
                    concertId, seatsBySection.size(), seatsBySection.keySet());

            // 6. 구역별 상세 정보 생성 (구역명 기준 정렬)
            List<SectionLayoutResponseDTO> sections = seatsBySection.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()) // 구역명으로 정렬 (A, B, C, VIP 등)
                    .map(entry -> SectionLayoutResponseDTO.from(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            // 7. 전체 좌석 배치도 생성
            SeatLayoutResponseDTO response = SeatLayoutResponseDTO.from(concertId, venueInfo, sections);

            log.info("좌석 배치도 조회 완료: concertId={}, 총좌석={}, 구역수={}, 예매가능률={}%",
                    concertId,
                    response.statistics().totalSeats(),
                    sections.size(),
                    String.format("%.1f", response.statistics().availabilityRate()));

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
     * 특정 구역의 좌석 배치 조회
     * @param concertId 콘서트 ID
     * @param sectionName 구역명 (A, B, VIP 등)
     * @return 해당 구역의 좌석 배치 정보
     * @throws BusinessException 콘서트나 구역을 찾을 수 없는 경우
     */
    public SectionLayoutResponseDTO getSectionLayout(Long concertId, String sectionName) {
        log.info("구역별 좌석 배치도 조회: concertId={}, section={}", concertId, sectionName);

        try {
            // 1. 콘서트 존재 여부 확인
            if (!concertRepository.existsById(concertId)) {
                log.warn("콘서트를 찾을 수 없음: concertId={}", concertId);
                throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
            }

            // 2. 입력값 검증
            if (sectionName == null || sectionName.trim().isEmpty()) {
                log.warn("구역명이 비어있음: concertId={}", concertId);
                throw new BusinessException(ErrorCode.INVALID_INPUT, "구역명을 입력해주세요.");
            }

            String trimmedSectionName = sectionName.trim();

            // 3. 해당 콘서트의 특정 구역 좌석들만 조회
            List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

            log.debug("전체 좌석 조회 완료: concertId={}, 총 좌석수={}", concertId, concertSeats.size());

            // 4. 특정 구역 필터링 (대소문자 무시)
            List<SeatDetailResponseDTO> sectionSeats = concertSeats.stream()
                    .filter(cs -> trimmedSectionName.equalsIgnoreCase(cs.getSeat().getSection()))
                    .map(SeatDetailResponseDTO::from)
                    .collect(Collectors.toList());

            if (sectionSeats.isEmpty()) {
                log.warn("해당 구역에 좌석이 없습니다: concertId={}, section={}", concertId, trimmedSectionName);

                // 사용자 친화적 에러 메시지 (사용 가능한 구역 목록 제공)
                List<String> availableSections = concertSeats.stream()
                        .map(cs -> cs.getSeat().getSection())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());

                log.info("사용 가능한 구역 목록: concertId={}, sections={}", concertId, availableSections);

                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND,
                        String.format("'%s' 구역을 찾을 수 없습니다. 사용 가능한 구역: %s",
                                trimmedSectionName, String.join(", ", availableSections)));
            }

            SectionLayoutResponseDTO response = SectionLayoutResponseDTO.from(trimmedSectionName, sectionSeats);

            log.info("구역별 좌석 배치도 조회 완료: concertId={}, section={}, 좌석수={}, 예매가능={}",
                    concertId, trimmedSectionName, response.totalSeats(), response.availableSeats());

            return response;

        } catch (BusinessException e) {
            log.error("구역별 좌석 배치도 조회 중 비즈니스 예외: concertId={}, section={}, error={}",
                    concertId, sectionName, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("구역별 좌석 배치도 조회 중 예상치 못한 오류: concertId={}, section={}",
                    concertId, sectionName, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "구역별 좌석 배치도 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 🔧 공연장 정보를 찾을 수 없을 때 사용할 대체 VenueDTO 생성
     * 시스템의 안정성을 위해 좌석 배치도는 여전히 제공하되, 공연장 정보는 기본값 사용
     *
     * @param venueName 콘서트에 등록된 공연장 이름
     * @return 대체 VenueDTO
     */
    private VenueDTO createFallbackVenueInfo(String venueName) {
        log.debug("대체 공연장 정보 생성: venueName={}", venueName);

        // VenueDTO의 생성자에 맞춰 임시 Venue 객체 생성 후 DTO 변환
        // 실제로는 존재하지 않는 공연장이지만 시스템 안정성을 위해 제공
        return new VenueDTO(new com.team03.ticketmon.venue.domain.Venue() {
            @Override
            public Long getVenueId() {
                return -1L; // 임시 ID (실제 DB에 없는 값)
            }

            @Override
            public String getName() {
                return venueName != null ? venueName : "알 수 없는 공연장";
            }

            @Override
            public Integer getCapacity() {
                return 0; // 알 수 없음
            }
        });
    }
}