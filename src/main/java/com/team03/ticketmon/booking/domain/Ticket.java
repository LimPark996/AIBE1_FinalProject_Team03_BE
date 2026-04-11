package com.team03.ticketmon.booking.domain;

import com.team03.ticketmon._global.entity.BaseTimeEntity;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Ticket 엔티티 — Booking 과 ConcertSeat 을 1:1 로 연결하는 좌석권.
 * 생성은 {@link #createTicket(ConcertSeat)} 팩토리를 통해서만 수행한다.
 */
@Entity
@Table(name = "tickets")
@Getter
@ToString(exclude = {"booking", "concertSeat"})
@EqualsAndHashCode(of = "ticketNumber", callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Ticket extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ticket_id")
	private Long ticketId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

    @OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concert_seat_id", nullable = false, unique = true)
	private ConcertSeat concertSeat;

	@Column(name = "ticket_number", nullable = false, unique = true)
	private String ticketNumber;

	@Column(precision = 10, scale = 2, nullable = false)
	private BigDecimal price;

	public static Ticket createTicket(ConcertSeat concertSeat) {
		Ticket ticket = new Ticket();
		ticket.ticketNumber = java.util.UUID.randomUUID().toString();
		ticket.price = concertSeat.getPrice();
		// ticket <-> concertSeat 양방향 연결
		ticket.setConcertSeatInternal(concertSeat);
		concertSeat.setTicket(ticket);
		return ticket;
	}

	protected void setBooking(Booking booking) {
		this.booking = booking;
	}

	protected void setConcertSeatInternal(ConcertSeat concertSeat) {
		this.concertSeat = concertSeat;
	}

	public void releaseConcertSeat() {
		this.concertSeat = null;
	}
}
