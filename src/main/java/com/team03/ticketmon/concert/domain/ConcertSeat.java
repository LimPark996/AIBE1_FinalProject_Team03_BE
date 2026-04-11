package com.team03.ticketmon.concert.domain;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.booking.domain.Ticket;
import com.team03.ticketmon.concert.domain.enums.SeatGrade;
import com.team03.ticketmon.venue.domain.Seat;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * ConcertSeat — 특정 콘서트의 좌석 엔티티(가격·등급 포함)
 * Concert와 Seat를 ManyToOne으로, Ticket과는 OneToOne으로 연결된다.
 */

@Entity
@Table(name = "concertSeats")
@Getter
@ToString(exclude = {"concert", "seat", "ticket"})
@EqualsAndHashCode(of = {"concertSeatId"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConcertSeat {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "concert_seat_id")
	private Long concertSeatId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concert_id", nullable = false)
	private Concert concert;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "seat_id", nullable = false)
	private Seat seat;

	@Column(length = 50, nullable = false)
	private SeatGrade grade;

	@Column(precision = 10, scale = 2, nullable = false)
	private BigDecimal price;

	@OneToOne(mappedBy = "concertSeat")
	private Ticket ticket;

	public void setTicket(Ticket ticket) {
		if (this.ticket != null && ticket != null && !this.ticket.equals(ticket)) {
			throw new BusinessException(ErrorCode.SERVER_ERROR, "좌석에 이미 티켓이 할당됨.");
		}
		this.ticket = ticket;
	}

	// 티켓 취소 시 좌석을 다시 AVAILABLE 상태로 만들기 위해 사용
	public void releaseTicket() {
		this.ticket = null;
	}

	// 좌석 정보를 문자열로 반환
	public String getSeatInfo() {
		if (this.seat == null) {
			return "Unknown";
		}
		// 좌석 정보 포맷: 구역-열-번호 (A-1-15)
		return String.format("%s-%s-%d",
				this.seat.getSection() != null ? this.seat.getSection() : "?",
				this.seat.getSeatRow() != null ? this.seat.getSeatRow() : "?",
				this.seat.getSeatNumber() != null ? this.seat.getSeatNumber() : 0);
	}
}
