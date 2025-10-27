package com.team03.ticketmon.booking.repository;

import com.team03.ticketmon.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 예매(Booking) 엔티티에 대한 데이터 접근을 처리
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {
    @Query("""
                select distinct b from Booking b
                join fetch b.concert
                left join fetch b.payment
                left join fetch b.tickets t
                left join fetch t.concertSeat cs
                where b.bookingNumber = :bookingNumber
            """)
    Optional<Booking> findByBookingNumber(String bookingNumber);

    @Query("""
                select distinct b from Booking b
                join fetch b.concert
                left join fetch b.payment
                left join fetch b.tickets t
                left join fetch t.concertSeat cs
                left join fetch cs.seat
                where b.userId = :userId
            """)
    List<Booking> findByUserId(@Param("userId") Long userId);

    // 결제 대기 중인 예매들 중에서 만료 시간이 지난 예매들을 찾음
    @Query("""
                select distinct b from Booking b
                join fetch b.concert
                left join fetch b.payment
                left join fetch b.tickets t
                left join fetch t.concertSeat cs
                where b.status = com.team03.ticketmon.booking.domain.BookingStatus.PENDING_PAYMENT
                  and b.createdAt <= :expirationTime
            """)
    List<Booking> findExpiredPendingBookings(@Param("expirationTime") LocalDateTime expirationTime);

    // 예매 ID로 찾는데, 콘서트 정보랑 티켓 목록도 같이 딸려오게 함
    @Query("""
            SELECT DISTINCT b
              FROM Booking b
             JOIN FETCH b.concert
             JOIN FETCH b.tickets
             WHERE b.id = :id
            """)
    Optional<Booking> findWithConcertAndTicketsById(@Param("id") Long id);
}