package com.team03.ticketmon.booking.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.team03.ticketmon._global.entity.BaseTimeEntity;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.payment.domain.entity.Payment;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Booking Entity
 * 예매 정보 관리
 */

@Entity
@Table(name = "bookings")
@Builder
@Getter
@Setter
@ToString(exclude = {"concert", "tickets", "payment"})
@EqualsAndHashCode(of = "bookingNumber", callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Booking extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "booking_id")
	private Long bookingId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "concert_id", nullable = false)
	private Concert concert;

	@Column(name = "booking_number", nullable = false, unique = true)
	private String bookingNumber;

	@Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(length = 20, nullable = false)
	private BookingStatus status;

	@Builder.Default
	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Ticket> tickets = new ArrayList<>();

	@OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private Payment payment;

	public void confirm() {
		this.status = BookingStatus.CONFIRMED;
	}
	public void cancel() {
		this.status = BookingStatus.CANCELED;
	}
	public void pending() {
		this.status = BookingStatus.PENDING_PAYMENT;
	}

	/**
	 * Booking과 Ticket 간의 양방향 관계를 설정
	 * 이 메서드는 주로 createBooking 메서드 내에서 호출되어 Booking 객체 생성 시 티켓 목록을 설정
	 * 부분 취소/환불과 같은 비즈니스 로직에 따라 티켓 목록이 변경될 때도 사용될 수 있음
	 * @param tickets Booking에 연결할 Ticket 목록
	 */
	public void setTickets(List<Ticket> tickets) {
		this.tickets.clear();
		if (tickets != null) {
			this.tickets.addAll(tickets);
			for (Ticket ticket : tickets) {
				ticket.setBooking(this);
			}
		}
	}

	/**
	 * 새로운 Booking 엔티티를 생성하는 정적 팩토리 메서드
	 * 이 메서드를 통해 Booking 객체의 생성 규칙과 초기 상태를 강제
	 * @param userId 예매를 시도하는 사용자 ID
	 * @param concert 대상 콘서트
	 * @param selectedSeats 선택한 콘서트 좌석 리스트
	 * @return 생성된 Booking 엔티티
	 */
	public static Booking createBooking(Long userId, Concert concert, List<ConcertSeat> selectedSeats) {
		// 1. 선택된 좌석들을 각각 createTicket()으로 맵핑
		List<Ticket> tickets = selectedSeats.stream()
			.map(Ticket::createTicket)
			.toList();

		// 2. Ticket들의 총액 계산
		BigDecimal ticketSubtotal = tickets.stream()
				.map(Ticket::getPrice)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		// 3. 수수료(serviceFee) 추가 (2000원)
		BigDecimal serviceFee = new BigDecimal("2000");
		BigDecimal totalAmount = ticketSubtotal.add(serviceFee);

		// 4. Booking 객체 생성
		Booking booking = Booking.builder()
			.userId(userId)
			.concert(concert)
			.bookingNumber(UUID.randomUUID().toString())
			.status(BookingStatus.PENDING_PAYMENT) // 결제 대기 상태
			.totalAmount(totalAmount)
			.build();

		// 5. Booking 객체에서 Ticket 리스트 설정 - 이 과정을 통해 Booking과 Ticket을 연결할 수 있다.
		booking.setTickets(tickets);
		return booking;
	}

	public void removeAllTickets() {
		for (Ticket ticket : new ArrayList<>(tickets)) {
			ticket.setBooking(null); // Ticket 객체에서 Booking은 모두 null로 처리한다.
			if (ticket.getConcertSeat() != null) { // 만약 ticket과 ConcertSeat이 여전히 연걸되어 있으면
				ticket.getConcertSeat().releaseTicket(); // ticket과 ConcertSeat의 연결 관계를 release한다.
			}
		}
		tickets.clear(); // ticket들의 리스트를 비운다.
	}
}
