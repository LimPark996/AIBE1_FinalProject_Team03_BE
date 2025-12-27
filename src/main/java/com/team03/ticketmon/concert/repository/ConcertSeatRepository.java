package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.domain.enums.SeatGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Concert Seat Repository
 * 콘서트 좌석 데이터 접근 계층
 */

@Repository
public interface ConcertSeatRepository extends JpaRepository<ConcertSeat, Long> {

	// 특정 콘서트의 모든 좌석 조회 (Fetch Join 최적화)
	@Query("SELECT cs FROM ConcertSeat cs " +
			"JOIN FETCH cs.seat s " +
			"JOIN FETCH cs.concert c " +
			"LEFT JOIN FETCH cs.ticket t " +
			"WHERE cs.concert.concertId = :concertId " +
			"ORDER BY s.section, s.seatRow, s.seatNumber")
	List<ConcertSeat> findByConcertIdWithDetails(@Param("concertId") Long concertId);

	// ConcertSeat ID 기반 존재성 검증
	// 컨트롤러와 서비스에서 실제 좌석 존재 여부 확인용
	@Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END " +
			"FROM ConcertSeat cs " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND cs.concertSeatId = :concertSeatId")
	boolean existsByConcertIdAndConcertSeatId(@Param("concertId") Long concertId,
											  @Param("concertSeatId") Long concertSeatId);

	// 특정 콘서트의 모든 좌석에서 티켓 삭제 (AVAILABLE 상태로 초기화)
	// 예매 시작 전 초기화용
	@Modifying
	@Query("DELETE FROM Ticket t " +
			"WHERE t.concertSeat.concert.concertId = :concertId")
	int bulkUpdateAllSeatsToAvailable(@Param("concertId") Long concertId);

	// 특정 콘서트의 모든 좌석과 가격 정보 조회 (가격 조회 전용)
	// SeatPriceService에서 사용
	@Query("SELECT cs FROM ConcertSeat cs " +
			"JOIN FETCH cs.seat s " +
			"WHERE cs.concert.concertId = :concertId " +
			"ORDER BY s.section, s.seatRow, s.seatNumber")
	List<ConcertSeat> findByConcertConcertIdWithSeat(@Param("concertId") Long concertId);

	// 선택된 좌석들의 가격 정보 조회 (가격 조회 전용)
	// SeatPriceService에서 사용
	@Query("SELECT cs FROM ConcertSeat cs " +
			"JOIN FETCH cs.seat s " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND cs.concertSeatId IN :seatIds " +
			"ORDER BY s.section, s.seatRow, s.seatNumber")
	List<ConcertSeat> findByConcertConcertIdAndConcertSeatIdInWithSeat(
			@Param("concertId") Long concertId,
			@Param("seatIds") List<Long> seatIds);

	@Query("SELECT DISTINCT cs.grade, cs.price " +
			"FROM ConcertSeat cs " +
			"WHERE cs.concert.concertId = :concertId " +
			"ORDER BY cs.grade")
	List<Object[]> findGradePricesByConcertId(@Param("concertId") Long concertId);

	@Query("SELECT cs FROM ConcertSeat cs " +
			"JOIN FETCH cs.seat s " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND cs.grade = :grade " +
			"ORDER BY s.section, s.seatRow, s.seatNumber")
	List<ConcertSeat> findByConcertIdAndGrade(
			@Param("concertId") Long concertId,
			@Param("grade") SeatGrade grade);

	@Query("SELECT cs FROM ConcertSeat cs " +
			"JOIN FETCH cs.seat s " +
			"WHERE cs.concert.concertId = :concertId " +
			"AND cs.grade = :grade " +
			"AND s.section = :section " +
			"ORDER BY s.seatRow, s.seatNumber")
	List<ConcertSeat> findByConcertIdAndGradeAndSection(
			@Param("concertId") Long concertId,
			@Param("grade") SeatGrade grade,
			@Param("section") String section);
	@Query("SELECT cs.grade, COUNT(cs) FROM ConcertSeat cs WHERE cs.concert.id = :concertId GROUP BY cs.grade")
	List<Object[]> countSeatsByGrade(@Param("concertId") Long concertId);

	/**
	 * 특정 등급의 구역별 좌석 수 조회
	 */
	@Query("SELECT s.section, COUNT(cs) FROM ConcertSeat cs " +
			"JOIN cs.seat s " +
			"WHERE cs.concert.id = :concertId AND cs.grade = :grade " +
			"GROUP BY s.section")
	List<Object[]> countSeatsByGradeAndSection(@Param("concertId") Long concertId,
											   @Param("grade") SeatGrade grade);
}