package com.team03.ticketmon.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team03.ticketmon.payment.domain.entity.PaymentCancelHistory;

/**
 * PaymentCancelHistoryRepository — 결제 취소 이력 저장/조회용 Repository.
 */
public interface PaymentCancelHistoryRepository extends JpaRepository<PaymentCancelHistory, Long> {
}
