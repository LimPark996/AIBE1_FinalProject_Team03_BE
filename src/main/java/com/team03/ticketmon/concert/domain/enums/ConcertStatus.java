package com.team03.ticketmon.concert.domain.enums;

/**
 * ConcertStatus — 콘서트 생명주기 상태
 * SCHEDULED(예매 전) → ON_SALE(예매 중) → BOOKING_CLOSED/SOLD_OUT → COMPLETED,
 * CANCELLED는 취소 종결 상태.
 */
public enum ConcertStatus {
	SCHEDULED,
	ON_SALE,
	BOOKING_CLOSED,
	SOLD_OUT,
	CANCELLED,
	COMPLETED
}
