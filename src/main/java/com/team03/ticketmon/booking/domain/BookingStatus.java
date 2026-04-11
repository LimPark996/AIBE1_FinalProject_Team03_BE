package com.team03.ticketmon.booking.domain;

/**
 * 예매 상태: PENDING_PAYMENT → CONFIRMED → (CANCELED | COMPLETED).
 */
public enum BookingStatus {
	PENDING_PAYMENT, // 결제 대기
	CONFIRMED,       // 예매 확정
	CANCELED,         // 예매 취소
	COMPLETED
}
