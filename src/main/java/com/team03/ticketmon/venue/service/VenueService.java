package com.team03.ticketmon.venue.service;

import com.team03.ticketmon.venue.domain.Venue;
import com.team03.ticketmon.venue.dto.VenueDTO;
import com.team03.ticketmon.venue.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 공연장 관련 비즈니스 로직을 처리하는 서비스 클래스
 * 현재 시스템에서는 주로 다른 서비스에서 공연장 정보를 조회하는 역할을 담당
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueService {

    private final VenueRepository venueRepository;

    // 공연장 이름으로 공연장 정보를 조회
    // Concert 엔티티의 venueName 필드를 통해 실제 Venue 정보를 조회하기 위해 추가
    public VenueDTO getVenueByName(String venueName) {

        if (venueName == null || venueName.trim().isEmpty()) {
            log.warn("공연장 이름이 비어있음");
            return null;
        }

        String trimmedVenueName = venueName.trim();
        Optional<Venue> venueOpt = venueRepository.findByName(trimmedVenueName);

        if (venueOpt.isEmpty()) {
            log.warn("공연장을 찾을 수 없음: venueName={}", trimmedVenueName);
            return null;
        }

        Venue venue = venueOpt.get(); // Venue Entity 객체 반환
        log.debug("공연장 이름으로 조회 성공: venueName={}, venueId={}, capacity={}",
            trimmedVenueName, venue.getVenueId(), venue.getCapacity());

        return new VenueDTO(venue);
    }
}