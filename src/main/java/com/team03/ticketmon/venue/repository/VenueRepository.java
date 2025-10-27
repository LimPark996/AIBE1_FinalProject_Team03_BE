package com.team03.ticketmon.venue.repository;

import com.team03.ticketmon.venue.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Venue 엔티티에 대한 데이터 접근을 처리하는 Spring Data JPA 리포지토리
 */
@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {

    // 공연장 이름으로 정확히 일치하는 공연장 조회
    // SeatLayoutService 에서 Concert의 venueName을 통해 Venue 정보를 찾기 위해 사용
    @Query("SELECT v FROM Venue v WHERE v.name = :name")
    Optional<Venue> findByName(@Param("name") String name);

}