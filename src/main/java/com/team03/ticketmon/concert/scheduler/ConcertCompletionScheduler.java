package com.team03.ticketmon.concert.scheduler;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.ConcertRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConcertCompletionScheduler {

	private final ConcertRepository concertRepository;

	/**
	 * 매분 실행하여 예매 시작 시간이 된 콘서트를 ON_SALE로 변경
	 */
	@Scheduled(fixedRate = 60000) // 1분마다
	@Transactional
	public void openBookingForScheduledConcerts() {
		log.info("🎫 예매 오픈 스케줄러 시작");

		try {
			LocalDateTime now = LocalDateTime.now();

			List<Concert> scheduledConcerts = concertRepository
					.findByStatusInOrderByConcertDateAsc(List.of(ConcertStatus.SCHEDULED));

			int openedCount = 0;

			for (Concert concert : scheduledConcerts) {
				if (shouldOpenBooking(concert, now)) {
					concert.setStatus(ConcertStatus.ON_SALE);
					concertRepository.save(concert);
					openedCount++;

					log.info("🎉 예매 오픈: ID={}, 제목='{}', SCHEDULED → ON_SALE",
							concert.getConcertId(), concert.getTitle());
				}
			}

			if (openedCount > 0) {
				log.info("✅ 예매 오픈 완료: {}건", openedCount);
			}
		} catch (Exception e) {
			log.error("❌ 예매 오픈 스케줄러 오류", e);
		}
	}

	/**
	 * 예매를 오픈해야 하는지 판단
	 */
	private boolean shouldOpenBooking(Concert concert, LocalDateTime now) {
		if (concert.getBookingStartDate() == null) {
			return false;
		}
		// 예매 시작 시간이 지났고, 아직 예매 종료 전인 경우
		boolean afterStart = now.isAfter(concert.getBookingStartDate())
				|| now.isEqual(concert.getBookingStartDate());
		boolean beforeEnd = concert.getBookingEndDate() == null
				|| now.isBefore(concert.getBookingEndDate());

		return afterStart && beforeEnd;
	}

	/**
	 * 예매 종료 시간이 지난 콘서트를 BOOKING_CLOSED로 변경
	 */
	@Scheduled(fixedRate = 60000)
	@Transactional
	public void closeBookingForExpiredConcerts() {
		log.info("🔒 예매 종료 스케줄러 시작");

		try {
			LocalDateTime now = LocalDateTime.now();

			List<Concert> onSaleConcerts = concertRepository
					.findByStatusInOrderByConcertDateAsc(List.of(ConcertStatus.ON_SALE));

			for (Concert concert : onSaleConcerts) {
				if (shouldCloseBooking(concert, now)) {
					concert.setStatus(ConcertStatus.BOOKING_CLOSED);
					concertRepository.save(concert);

					log.info("🔒 예매 종료: ID={}, 제목='{}', ON_SALE → BOOKING_CLOSED",
							concert.getConcertId(), concert.getTitle());
				}
			}
		} catch (Exception e) {
			log.error("❌ 예매 종료 스케줄러 오류", e);
		}
	}

	private boolean shouldCloseBooking(Concert concert, LocalDateTime now) {
		return concert.getBookingEndDate() != null
				&& now.isAfter(concert.getBookingEndDate());
	}

	/**
	 * 매시간 실행하여 공연 종료된 콘서트들을 COMPLETED로 변경
	 */
	@Scheduled(fixedRate = 60000) // 1분마다
	@Transactional
	public void completeFinishedConcerts() {
		log.info("🕐 공연 완료 처리 스케줄러 시작");

		try {
			// COMPLETED가 아닌 모든 상태의 콘서트 조회
			List<ConcertStatus> activeStatuses = Arrays.asList(
				ConcertStatus.SCHEDULED,
				ConcertStatus.ON_SALE,
				ConcertStatus.SOLD_OUT
				// CANCELLED는 제외 - 취소된 공연은 COMPLETED로 바뀌지 않음
			);

			List<Concert> activeConcerts = concertRepository
				.findByStatusInOrderByConcertDateAsc(activeStatuses);

			int completedCount = 0;
			boolean hasCompletedConcerts = false; // 🔥 캐시 무효화 필요 여부 플래그

			for (Concert concert : activeConcerts) {
				if (shouldBeCompleted(concert)) {
					ConcertStatus oldStatus = concert.getStatus();
					concert.setStatus(ConcertStatus.COMPLETED);

					concertRepository.save(concert);
					completedCount++;
					hasCompletedConcerts = true; // 🔥 완료 처리가 발생했음을 표시

					log.info("✅ 공연 완료 처리: ID={}, 제목='{}', {} → COMPLETED",
						concert.getConcertId(), concert.getTitle(), oldStatus);
				}
			}
		} catch (Exception e) {
			log.error("❌ 공연 완료 처리 스케줄러 오류", e);
		}
	}

	/**
	 * 공연이 완료되어야 하는지 판단
	 * 공연 종료 시간 + 30분 후에 완료 처리 (여유시간 확보)
	 */
	private boolean shouldBeCompleted(Concert concert) {
		if (concert.getConcertDate() == null || concert.getEndTime() == null) {
			return false;
		}

		// 공연 종료 30분 후에 COMPLETED 처리
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime concertEndDateTime = concert.getConcertDate().atTime(concert.getEndTime());
		LocalDateTime completionTime = concertEndDateTime.plusMinutes(30); // 종료 30분 후

		return now.isAfter(completionTime);
	}
}