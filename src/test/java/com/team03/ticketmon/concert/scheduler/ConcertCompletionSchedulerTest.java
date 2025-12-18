package com.team03.ticketmon.concert.scheduler;

import com.team03.ticketmon.batch.domain.BatchExecutionLog;
import com.team03.ticketmon.batch.domain.BatchStatus;
import com.team03.ticketmon.batch.repository.BatchExecutionLogRepository;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.team03.ticketmon.config.BaseIntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Transactional
@DisplayName("콘서트 상태 변경 스케줄러 테스트")
class ConcertCompletionSchedulerTest extends BaseIntegrationTest {

    @Autowired
    private ConcertCompletionScheduler scheduler;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private BatchExecutionLogRepository batchLogRepository;

    // ========== 헬퍼 메서드 ==========

    private Concert createConcert(String title, ConcertStatus status,
                                  LocalDateTime bookingStart, LocalDateTime bookingEnd,
                                  LocalDate concertDate, LocalTime endTime) {
        Concert concert = Concert.builder()
                .title(title)
                .status(status)
                .bookingStartDate(bookingStart)
                .bookingEndDate(bookingEnd)
                .concertDate(concertDate)
                .endTime(endTime)
                .artist("테스트 아티스트")
                .sellerId(1L)
                .venueName("테스트 공연장")
                .startTime(LocalTime.of(19, 0))
                .totalSeats(100)
                .build();
        return concertRepository.save(concert);
    }

    // ========== 예매 오픈 테스트 (SCHEDULED → ON_SALE) ==========

    @Nested
    @DisplayName("예매 오픈 스케줄러 (openBookingForScheduledConcerts)")
    class OpenBookingTest {

        @Test
        @DisplayName("예매 시작 시간이 지났으면 ON_SALE로 변경된다")
        void 예매시작_지남_ON_SALE로변경() {
            // given - 예매 시작이 1시간 전
            Concert concert = createConcert(
                    "예매 오픈 콘서트",
                    ConcertStatus.SCHEDULED,
                    LocalDateTime.now().minusHours(1),   // 예매 시작: 1시간 전
                    LocalDateTime.now().plusDays(7),     // 예매 종료: 7일 후
                    LocalDate.now().plusDays(14),
                    LocalTime.of(22, 0)
            );

            // when
            scheduler.openBookingForScheduledConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.ON_SALE);

            System.out.println("========== 예매 오픈 결과 ==========");
            System.out.println("SCHEDULED → " + updated.getStatus());
        }

        @Test
        @DisplayName("예매 시작 시간이 안 됐으면 SCHEDULED 유지")
        void 예매시작_안됨_SCHEDULED유지() {
            // given - 예매 시작이 1시간 후
            Concert concert = createConcert(
                    "아직 예매 안 열린 콘서트",
                    ConcertStatus.SCHEDULED,
                    LocalDateTime.now().plusHours(1),    // 예매 시작: 1시간 후
                    LocalDateTime.now().plusDays(7),
                    LocalDate.now().plusDays(14),
                    LocalTime.of(22, 0)
            );

            // when
            scheduler.openBookingForScheduledConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.SCHEDULED);

            System.out.println("========== 예매 시작 전 ==========");
            System.out.println("상태 유지: " + updated.getStatus());
        }

        @Test
        @DisplayName("이미 예매 종료 시간이 지났으면 ON_SALE로 변경 안 됨")
        void 예매종료_지남_변경안됨() {
            // given - 예매 시작도 지났고 종료도 지남
            Concert concert = createConcert(
                    "예매 끝난 콘서트",
                    ConcertStatus.SCHEDULED,
                    LocalDateTime.now().minusDays(7),    // 예매 시작: 7일 전
                    LocalDateTime.now().minusHours(1),   // 예매 종료: 1시간 전
                    LocalDate.now().plusDays(1),
                    LocalTime.of(22, 0)
            );

            // when
            scheduler.openBookingForScheduledConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.SCHEDULED);

            System.out.println("========== 예매 기간 지남 ==========");
            System.out.println("변경 안 됨: " + updated.getStatus());
        }

        @Test
        @DisplayName("ON_SALE 상태 콘서트는 처리 대상 아님")
        void ON_SALE_처리대상아님() {
            // given - 이미 ON_SALE
            Concert concert = createConcert(
                    "이미 판매중 콘서트",
                    ConcertStatus.ON_SALE,
                    LocalDateTime.now().minusHours(1),
                    LocalDateTime.now().plusDays(7),
                    LocalDate.now().plusDays(14),
                    LocalTime.of(22, 0)
            );

            // when
            scheduler.openBookingForScheduledConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.ON_SALE);
        }
    }

    // ========== 예매 종료 테스트 (ON_SALE → BOOKING_CLOSED) ==========

    @Nested
    @DisplayName("예매 종료 스케줄러 (closeBookingForExpiredConcerts)")
    class CloseBookingTest {

        @Test
        @DisplayName("예매 종료 시간이 지났으면 BOOKING_CLOSED로 변경된다")
        void 예매종료_지남_BOOKING_CLOSED로변경() {
            // given - 예매 종료가 1시간 전
            Concert concert = createConcert(
                    "예매 종료 콘서트",
                    ConcertStatus.ON_SALE,
                    LocalDateTime.now().minusDays(7),
                    LocalDateTime.now().minusHours(1),   // 예매 종료: 1시간 전
                    LocalDate.now().plusDays(1),
                    LocalTime.of(22, 0)
            );

            // when
            scheduler.closeBookingForExpiredConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.BOOKING_CLOSED);

            System.out.println("========== 예매 종료 결과 ==========");
            System.out.println("ON_SALE → " + updated.getStatus());
        }

        @Test
        @DisplayName("예매 종료 시간이 안 지났으면 ON_SALE 유지")
        void 예매종료_안지남_ON_SALE유지() {
            // given - 예매 종료가 1시간 후
            Concert concert = createConcert(
                    "아직 예매중 콘서트",
                    ConcertStatus.ON_SALE,
                    LocalDateTime.now().minusDays(7),
                    LocalDateTime.now().plusHours(1),    // 예매 종료: 1시간 후
                    LocalDate.now().plusDays(7),
                    LocalTime.of(22, 0)
            );

            // when
            scheduler.closeBookingForExpiredConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.ON_SALE);

            System.out.println("========== 예매 종료 전 ==========");
            System.out.println("상태 유지: " + updated.getStatus());
        }

        @Test
        @DisplayName("SCHEDULED 상태는 처리 대상 아님")
        void SCHEDULED_처리대상아님() {
            // given
            Concert concert = createConcert(
                    "아직 예매 안 열린 콘서트",
                    ConcertStatus.SCHEDULED,
                    LocalDateTime.now().minusDays(7),
                    LocalDateTime.now().minusHours(1),
                    LocalDate.now().plusDays(1),
                    LocalTime.of(22, 0)
            );

            // when
            scheduler.closeBookingForExpiredConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.SCHEDULED);
        }
    }

    // ========== 공연 완료 테스트 (→ COMPLETED) ==========

    @Nested
    @DisplayName("공연 완료 스케줄러 (completeFinishedConcerts)")
    class CompleteFinishedTest {

        @Test
        @DisplayName("공연 종료 30분 후 COMPLETED로 변경된다")
        void 공연종료_30분후_COMPLETED로변경() {
            // given - 공연이 1시간 전에 끝남 (30분 지남)
            Concert concert = createConcert(
                    "공연 끝난 콘서트",
                    ConcertStatus.ON_SALE,
                    LocalDateTime.now().minusDays(7),
                    LocalDateTime.now().minusDays(1),
                    LocalDate.now(),                      // 오늘 공연
                    LocalTime.now().minusHours(1)         // 1시간 전 종료
            );

            // when
            scheduler.completeFinishedConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.COMPLETED);

            System.out.println("========== 공연 완료 결과 ==========");
            System.out.println("ON_SALE → " + updated.getStatus());
        }

        @Test
        @DisplayName("공연 종료 후 30분 이내면 COMPLETED로 변경 안 됨")
        void 공연종료_30분이내_변경안됨() {
            // given - 공연이 10분 전에 끝남
            Concert concert = createConcert(
                    "방금 끝난 콘서트",
                    ConcertStatus.ON_SALE,
                    LocalDateTime.now().minusDays(7),
                    LocalDateTime.now().minusDays(1),
                    LocalDate.now(),
                    LocalTime.now().minusMinutes(10)      // 10분 전 종료
            );

            // when
            scheduler.completeFinishedConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.ON_SALE);

            System.out.println("========== 30분 미경과 ==========");
            System.out.println("상태 유지: " + updated.getStatus());
        }

        @Test
        @DisplayName("아직 공연 전이면 변경 안 됨")
        void 공연전_변경안됨() {
            // given - 공연이 내일
            Concert concert = createConcert(
                    "아직 안 한 콘서트",
                    ConcertStatus.ON_SALE,
                    LocalDateTime.now().minusDays(7),
                    LocalDateTime.now().minusDays(1),
                    LocalDate.now().plusDays(1),          // 내일 공연
                    LocalTime.of(22, 0)
            );

            // when
            scheduler.completeFinishedConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.ON_SALE);

            System.out.println("========== 공연 전 ==========");
            System.out.println("상태 유지: " + updated.getStatus());
        }

        @Test
        @DisplayName("SOLD_OUT 상태도 공연 완료 처리 대상이다")
        void SOLD_OUT_완료처리대상() {
            // given
            Concert concert = createConcert(
                    "매진된 콘서트",
                    ConcertStatus.SOLD_OUT,
                    LocalDateTime.now().minusDays(7),
                    LocalDateTime.now().minusDays(1),
                    LocalDate.now(),
                    LocalTime.now().minusHours(1)
            );

            // when
            scheduler.completeFinishedConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.COMPLETED);

            System.out.println("========== SOLD_OUT → COMPLETED ==========");
        }

        @Test
        @DisplayName("CANCELLED 상태는 완료 처리 대상 아님")
        void CANCELLED_완료처리대상아님() {
            // given
            Concert concert = createConcert(
                    "취소된 콘서트",
                    ConcertStatus.CANCELLED,
                    LocalDateTime.now().minusDays(7),
                    LocalDateTime.now().minusDays(1),
                    LocalDate.now(),
                    LocalTime.now().minusHours(1)
            );

            // when
            scheduler.completeFinishedConcerts();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ConcertStatus.CANCELLED);

            System.out.println("========== CANCELLED 유지 ==========");
        }
    }

    // ========== 배치 로그 테스트 ==========

    @Nested
    @DisplayName("배치 로그 저장 테스트")
    class BatchLogTest {

        @Test
        @DisplayName("예매 오픈 스케줄러 실행 시 로그 저장")
        void 예매오픈_로그저장() {
            // given
            Concert concert = createConcert(
                    "로그 테스트 콘서트",
                    ConcertStatus.SCHEDULED,
                    LocalDateTime.now().minusHours(1),
                    LocalDateTime.now().plusDays(7),
                    LocalDate.now().plusDays(14),
                    LocalTime.of(22, 0)
            );

            // when
            scheduler.openBookingForScheduledConcerts();

            // then
            List<BatchExecutionLog> logs = batchLogRepository.findByJobNameOrderByStartedAtDesc("CONCERT_OPEN");
            assertThat(logs).isNotEmpty();

            BatchExecutionLog log = logs.get(0);
            assertThat(log.getStatus()).isEqualTo(BatchStatus.SUCCESS);
            assertThat(log.getDurationMs()).isNotNull();

            System.out.println("========== 예매 오픈 로그 ==========");
            System.out.println("Job: " + log.getJobName());
            System.out.println("상태: " + log.getStatus());
            System.out.println("소요시간: " + log.getDurationMs() + "ms");
        }
    }

    // ========== 복합 시나리오 ==========

    @Nested
    @DisplayName("복합 시나리오 테스트")
    class ComplexScenarioTest {

        @Test
        @DisplayName("전체 스케줄러 순차 실행 시나리오")
        void 전체스케줄러_순차실행() {
            // given
            // 콘서트1: SCHEDULED + 예매 시작됨 → ON_SALE 되어야 함
            Concert concert1 = createConcert(
                    "예매 오픈 대상",
                    ConcertStatus.SCHEDULED,
                    LocalDateTime.now().minusHours(1),
                    LocalDateTime.now().plusDays(7),
                    LocalDate.now().plusDays(14),
                    LocalTime.of(22, 0)
            );

            // 콘서트2: ON_SALE + 예매 종료됨 → BOOKING_CLOSED 되어야 함
            Concert concert2 = createConcert(
                    "예매 종료 대상",
                    ConcertStatus.ON_SALE,
                    LocalDateTime.now().minusDays(7),
                    LocalDateTime.now().minusHours(1),
                    LocalDate.now().plusDays(1),
                    LocalTime.of(22, 0)
            );

            // 콘서트3: ON_SALE + 공연 끝남 → COMPLETED 되어야 함
            Concert concert3 = createConcert(
                    "공연 완료 대상",
                    ConcertStatus.ON_SALE,
                    LocalDateTime.now().minusDays(14),
                    LocalDateTime.now().plusDays(1),
                    LocalDate.now(),
                    LocalTime.now().minusHours(1)
            );
            // when - 순차 실행
            scheduler.openBookingForScheduledConcerts();
            scheduler.closeBookingForExpiredConcerts();
            scheduler.completeFinishedConcerts();

            // then
            Concert updated1 = concertRepository.findById(concert1.getConcertId()).orElseThrow();
            Concert updated2 = concertRepository.findById(concert2.getConcertId()).orElseThrow();
            Concert updated3 = concertRepository.findById(concert3.getConcertId()).orElseThrow();

            assertThat(updated1.getStatus()).isEqualTo(ConcertStatus.ON_SALE);
            assertThat(updated2.getStatus()).isEqualTo(ConcertStatus.BOOKING_CLOSED);
            assertThat(updated3.getStatus()).isEqualTo(ConcertStatus.COMPLETED);

            System.out.println("========== 전체 스케줄러 결과 ==========");
            System.out.println("콘서트1: SCHEDULED → " + updated1.getStatus());
            System.out.println("콘서트2: ON_SALE → " + updated2.getStatus());
            System.out.println("콘서트3: ON_SALE → " + updated3.getStatus());
        }
    }
}