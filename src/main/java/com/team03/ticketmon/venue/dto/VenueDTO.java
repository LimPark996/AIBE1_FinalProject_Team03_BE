package com.team03.ticketmon.venue.dto;

import com.team03.ticketmon.venue.domain.Venue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공연장 정보 전송을 위한 DTO(Data Transfer Object)들을 담는 클래스
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VenueDTO {
    private Long venueId;
    private String name;

    public VenueDTO(Venue venue) {
        this.venueId = venue.getVenueId();
        this.name = venue.getName();
    }
}