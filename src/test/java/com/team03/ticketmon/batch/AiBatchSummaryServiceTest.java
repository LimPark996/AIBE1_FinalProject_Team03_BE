package com.team03.ticketmon.batch;

import com.team03.ticketmon.batch.domain.BatchExecutionLog;
import com.team03.ticketmon.batch.domain.BatchStatus;
import com.team03.ticketmon.batch.repository.BatchExecutionLogRepository;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.Review;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.dto.AiBatchSummaryResultDTO;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ReviewRepository;
import com.team03.ticketmon.concert.service.AiBatchSummaryService;
import com.team03.ticketmon.concert.service.AiSummaryService;
import com.team03.ticketmon.concert.util.ReviewChecksumGenerator;
import com.team03.ticketmon.config.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@Transactional
@DisplayName("AI 배치 요약 서비스 테스트")
class AiBatchSummaryServiceTest extends BaseIntegrationTest {

    @Autowired
    private AiBatchSummaryService aiBatchService;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private BatchExecutionLogRepository batchLogRepository;

    @Autowired
    private ReviewChecksumGenerator checksumGenerator;

    @MockitoBean
    private AiSummaryService aiSummaryService;

    // 테스트에서 공통으로 쓸 가짜 AI 응답
    private static final String MOCK_AI_SUMMARY = "이 콘서트는 관객들에게 좋은 평가를 받았습니다. 음향과 무대 연출이 특히 호평을 받았으며, 전반적으로 만족스러운 공연이었습니다.";

    @BeforeEach
    void setUp() {
        // 모든 테스트에서 AI 서비스는 가짜 응답 리턴
        when(aiSummaryService.generateSummary(anyList())).thenReturn(MOCK_AI_SUMMARY);
    }

    // ========== 헬퍼 메서드 ==========

    /**
     * 테스트용 콘서트를 생성하고 저장하는 헬퍼 메서드
     */
    private Concert createConcert(String title, String aiSummary, Integer aiSummaryReviewCount,
                                  LocalDateTime aiSummaryGeneratedAt, String checksum) {
        Concert concert = Concert.builder()
                .title(title)
                .status(ConcertStatus.ON_SALE)
                .concertDate(LocalDate.now().plusDays(14))
                .endTime(LocalTime.of(22, 0))
                .aiSummary(aiSummary)
                .aiSummaryReviewCount(aiSummaryReviewCount)
                .aiSummaryGeneratedAt(aiSummaryGeneratedAt)
                .aiSummaryReviewChecksum(checksum)
                .artist("테스트 아티스트")
                .sellerId(1L)
                .venueName("테스트 공연장")
                .startTime(LocalTime.of(19, 0))
                .totalSeats(100)
                .bookingStartDate(LocalDateTime.now().minusDays(7))
                .bookingEndDate(LocalDateTime.now().plusDays(7))
                .build();
        return concertRepository.save(concert);
    }

    /**
     * 특정 콘서트에 리뷰를 n개 생성하는 헬퍼 메서드
     */
    private void createReviews(Concert concert, int count) {
        for (int i = 1; i <= count; i++) {
            Review review = Review.builder()
                    .concert(concert)
                    .description("테스트 리뷰입니다. 이 공연은 정말 좋았습니다. 리뷰 번호: " + i)
                    .rating(4 + (i % 2))
                    .userId((long) i)
                    .userNickname("테스트유저" + i)
                    .title("테스트 리뷰 제목 " + i)
                    .build();
            reviewRepository.save(review);
        }
    }

    /**
     * 특정 콘서트의 리뷰들로 체크섬 생성
     */
    private String getChecksumForConcert(Concert concert) {
        List<Review> reviews = reviewRepository.findValidReviewsForAiSummary(concert.getConcertId());
        return checksumGenerator.generateChecksum(reviews);
    }

    // ========== 배치 로그 테스트 ==========

    @Nested
    @DisplayName("배치 실행 로그 테스트")
    class BatchLogTest {

        @Test
        @DisplayName("배치 실행 시 batch_execution_log에 기록된다")
        void 배치실행시_로그저장() {
            // given - 리뷰 12개 콘서트
            Concert concert = createConcert("로그 테스트 콘서트", null, 0, null, null);
            createReviews(concert, 12);

            // when
            aiBatchService.processBatch();

            // then
            List<BatchExecutionLog> logs = batchLogRepository.findByJobNameOrderByStartedAtDesc("AI_SUMMARY");

            assertThat(logs).isNotEmpty();

            BatchExecutionLog latestLog = logs.get(0);
            assertThat(latestLog.getJobName()).isEqualTo("AI_SUMMARY");
            assertThat(latestLog.getStartedAt()).isNotNull();
            assertThat(latestLog.getFinishedAt()).isNotNull();
            assertThat(latestLog.getDurationMs()).isNotNull();
            assertThat(latestLog.getDurationMs()).isGreaterThanOrEqualTo(0);
            assertThat(latestLog.getStatus()).isIn(BatchStatus.SUCCESS, BatchStatus.PARTIAL_FAIL);

            printLog("배치 로그 저장", latestLog);
        }

        @Test
        @DisplayName("처리 대상이 없어도 로그는 기록된다")
        void 처리대상없어도_로그저장() {
            // given - 아무 콘서트도 없음

            // when
            aiBatchService.processBatch();

            // then
            List<BatchExecutionLog> logs = batchLogRepository.findByJobNameOrderByStartedAtDesc("AI_SUMMARY");

            assertThat(logs).isNotEmpty();
            BatchExecutionLog latestLog = logs.get(0);
            assertThat(latestLog.getTotalCount()).isEqualTo(0);
            assertThat(latestLog.getStatus()).isEqualTo(BatchStatus.SUCCESS);

            printLog("빈 배치 로그", latestLog);
        }
    }

    // ========== 조건 1: INITIAL_CREATION ==========

    @Nested
    @DisplayName("조건1: AI 요약이 없으면 생성 (INITIAL_CREATION)")
    class InitialCreationTest {

        @Test
        @DisplayName("aiSummary가 null이면 새로 생성한다")
        void aiSummary_null이면_생성() {
            // given
            Concert concert = createConcert("신규 콘서트", null, 0, null, null);
            createReviews(concert, 12);

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();

            assertThat(updated.getAiSummary()).isNotNull();
            assertThat(updated.getAiSummary()).isEqualTo(MOCK_AI_SUMMARY);
            assertThat(updated.getAiSummaryGeneratedAt()).isNotNull();
            assertThat(updated.getAiSummaryReviewCount()).isEqualTo(12);
            assertThat(updated.getAiSummaryReviewChecksum()).isNotNull();

            System.out.println("========== INITIAL_CREATION 결과 ==========");
            System.out.println("AI 요약 생성됨: " + updated.getAiSummary().substring(0, 30) + "...");
        }

        @Test
        @DisplayName("aiSummary가 빈 문자열이면 새로 생성한다")
        void aiSummary_빈문자열이면_생성() {
            // given
            Concert concert = createConcert("빈 요약 콘서트", "", 0, null, null);
            createReviews(concert, 12);

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummary()).isEqualTo(MOCK_AI_SUMMARY);

            System.out.println("========== 빈 문자열 → 생성 ==========");
        }
    }

    // ========== 조건 2, 3: COUNT_CHANGED ==========

    @Nested
    @DisplayName("조건2,3: 리뷰 수 변화 (COUNT_CHANGED)")
    class CountChangedTest {

        @Test
        @DisplayName("리뷰가 3개 이상 증가하면 재생성한다 (significantCountChange)")
        void 리뷰수_3개이상_증가() {
            // given - 기존에 10개 리뷰로 요약 생성된 상태
            Concert concert = createConcert("리뷰 증가 콘서트", "기존 AI 요약", 10,
                    LocalDateTime.now(), "old_checksum");
            createReviews(concert, 14);  // 14개로 증가 (차이 4개 >= 3개)

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummary()).isEqualTo(MOCK_AI_SUMMARY);  // 새로 생성됨
            assertThat(updated.getAiSummaryReviewCount()).isEqualTo(14);

            System.out.println("========== COUNT_CHANGED (절대값) 결과 ==========");
            System.out.println("이전 리뷰 수: 10 → 현재: 14 (차이 4개)");
        }

        @Test
        @DisplayName("리뷰가 20% 이상 증가하면 재생성한다 (significantCountChangeRatio)")
        void 리뷰수_20퍼센트이상_증가() {
            // given - 기존에 20개 리뷰로 요약 생성된 상태
            Concert concert = createConcert("비율 테스트 콘서트", "기존 AI 요약", 20,
                    LocalDateTime.now(), "old_checksum");
            createReviews(concert, 25);  // 25개로 증가 (25% 증가 >= 20%)

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummary()).isEqualTo(MOCK_AI_SUMMARY);
            assertThat(updated.getAiSummaryReviewCount()).isEqualTo(25);

            System.out.println("========== COUNT_CHANGED (비율) 결과 ==========");
            System.out.println("이전 리뷰 수: 20 → 현재: 25 (25% 증가)");
        }

        @Test
        @DisplayName("리뷰 수 변화가 미미하면 스킵한다")
        void 리뷰수_변화_미미하면_스킵() {
            // given - 기존에 10개 리뷰로 요약 생성, 현재도 11개 (차이 1개, 10%)
            Concert concert = createConcert("변화 없는 콘서트", "기존 AI 요약", 10,
                    LocalDateTime.now(), null);
            createReviews(concert, 11);

            // 체크섬을 현재 리뷰 상태로 맞춰줌 (내용 변화 없음 처리)
            String currentChecksum = getChecksumForConcert(concert);
            concert.setAiSummaryReviewChecksum(currentChecksum);
            concertRepository.save(concert);

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummary()).isEqualTo("기존 AI 요약");  // 변경 안 됨

            System.out.println("========== 리뷰 수 변화 미미 → 스킵 ==========");
            System.out.println("이전: 10개, 현재: 11개 (차이 1개, 10%) → 조건 미달");
        }
    }

    // ========== 조건 4: CONTENT_CHANGED ==========

    @Nested
    @DisplayName("조건4: 리뷰 내용 변화 (CONTENT_CHANGED)")
    class ContentChangedTest {

        @Test
        @DisplayName("리뷰 내용이 변경되면 재생성한다 (체크섬 다름)")
        void 리뷰내용_변경되면_재생성() {
            // given - 기존 요약 있고, 체크섬이 다른 상태
            Concert concert = createConcert("내용 변경 콘서트", "기존 AI 요약", 12,
                    LocalDateTime.now(), "old_different_checksum");  // 현재와 다른 체크섬
            createReviews(concert, 12);

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummary()).isEqualTo(MOCK_AI_SUMMARY);  // 새로 생성됨

            System.out.println("========== CONTENT_CHANGED 결과 ==========");
            System.out.println("체크섬 불일치 → 재생성");
        }

        @Test
        @DisplayName("리뷰 내용이 동일하면 스킵한다 (체크섬 동일)")
        void 리뷰내용_동일하면_스킵() {
            // given
            Concert concert = createConcert("내용 동일 콘서트", "기존 AI 요약", 12,
                    LocalDateTime.now(), null);
            createReviews(concert, 12);

            // 체크섬을 현재 상태로 맞춤
            String currentChecksum = getChecksumForConcert(concert);
            concert.setAiSummaryReviewChecksum(currentChecksum);
            concertRepository.save(concert);

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummary()).isEqualTo("기존 AI 요약");  // 변경 안 됨

            System.out.println("========== 체크섬 동일 → 스킵 ==========");
        }
    }

    // ========== 조건 5: TIME_BASED_UPDATE ==========

    @Nested
    @DisplayName("조건5: 시간 기반 업데이트 (TIME_BASED_UPDATE)")
    class TimeBasedUpdateTest {

        @Test
        @DisplayName("마지막 생성 후 1시간 이상 지나면 재생성한다")
        void 시간경과하면_재생성() {
            // given - 2시간 전에 생성된 요약
            Concert concert = createConcert("시간 경과 콘서트", "기존 AI 요약", 12,
                    LocalDateTime.now().minusHours(2), null);  // 2시간 전
            createReviews(concert, 12);

            // 체크섬 맞춰서 CONTENT_CHANGED 조건 제외
            String currentChecksum = getChecksumForConcert(concert);
            concert.setAiSummaryReviewChecksum(currentChecksum);
            concertRepository.save(concert);

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummary()).isEqualTo(MOCK_AI_SUMMARY);

            System.out.println("========== TIME_BASED_UPDATE 결과 ==========");
            System.out.println("마지막 생성: 2시간 전 → 재생성");
        }

        @Test
        @DisplayName("1시간 이내면 스킵한다")
        void 시간_미경과면_스킵() {
            // given - 30분 전에 생성된 요약
            Concert concert = createConcert("최근 생성 콘서트", "기존 AI 요약", 12,
                    LocalDateTime.now().minusMinutes(30), null);  // 30분 전
            createReviews(concert, 12);

            // 체크섬 맞춤
            String currentChecksum = getChecksumForConcert(concert);
            concert.setAiSummaryReviewChecksum(currentChecksum);
            concertRepository.save(concert);

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummary()).isEqualTo("기존 AI 요약");

            System.out.println("========== 1시간 미경과 → 스킵 ==========");
            System.out.println("마지막 생성: 30분 전");
        }
    }

    // ========== 조건 6: 최소 리뷰 수 미달 ==========

    @Nested
    @DisplayName("사전 필터링: 최소 리뷰 수")
    class MinReviewCountTest {

        @Test
        @DisplayName("리뷰 10개 미만이면 처리 대상에서 제외된다")
        void 리뷰_10개미만_제외() {
            // given
            Concert concert = createConcert("리뷰 부족 콘서트", null, 0, null, null);
            createReviews(concert, 5);  // 5개만

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummary()).isNull();  // 생성 안 됨

            System.out.println("========== 리뷰 10개 미만 → 제외 ==========");
        }

        @Test
        @DisplayName("리뷰가 정확히 10개면 처리 대상이 된다")
        void 리뷰_정확히10개_처리() {
            // given
            Concert concert = createConcert("리뷰 10개 콘서트", null, 0, null, null);
            createReviews(concert, 10);

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummary()).isEqualTo(MOCK_AI_SUMMARY);

            System.out.println("========== 리뷰 정확히 10개 → 처리 ==========");
        }
    }

    // ========== 실패 처리 테스트 ==========

    @Nested
    @DisplayName("실패 처리 테스트")
    class FailureHandlingTest {

        @Test
        @DisplayName("AI 요약 실패 시 retryCount가 증가한다")
        void 실패시_retryCount_증가() {
            // given
            when(aiSummaryService.generateSummary(anyList()))
                    .thenThrow(new RuntimeException("AI API 오류"));

            Concert concert = createConcert("실패 테스트 콘서트", null, 0, null, null);
            createReviews(concert, 12);

            // when
            aiBatchService.processBatch();

            // then
            Concert updated = concertRepository.findById(concert.getConcertId()).orElseThrow();
            assertThat(updated.getAiSummaryRetryCount()).isEqualTo(1);
            assertThat(updated.getAiSummaryLastFailedAt()).isNotNull();

            System.out.println("========== 실패 처리 결과 ==========");
            System.out.println("retryCount: " + updated.getAiSummaryRetryCount());
            System.out.println("lastFailedAt: " + updated.getAiSummaryLastFailedAt());
        }
    }

    // ========== 복합 시나리오 ==========

    @Nested
    @DisplayName("복합 시나리오 테스트")
    class ComplexScenarioTest {

        @Test
        @DisplayName("여러 콘서트 중 조건 맞는 것만 처리된다")
        void 여러콘서트_선별처리() {
            // given
            // 콘서트1: 요약 없음 + 리뷰 12개 → 처리 대상
            Concert concert1 = createConcert("처리대상 콘서트", null, 0, null, null);
            createReviews(concert1, 12);

            // 콘서트2: 리뷰 5개 → 제외
            Concert concert2 = createConcert("리뷰부족 콘서트", null, 0, null, null);
            createReviews(concert2, 5);

            // 콘서트3: 최근 생성 + 변화 없음 → 스킵
            Concert concert3 = createConcert("스킵대상 콘서트", "기존 요약", 12,
                    LocalDateTime.now().minusMinutes(30), null);
            createReviews(concert3, 12);
            String checksum3 = getChecksumForConcert(concert3);
            concert3.setAiSummaryReviewChecksum(checksum3);
            concertRepository.save(concert3);

            // when
            AiBatchSummaryResultDTO result = aiBatchService.processBatch();

            // then
            Concert updated1 = concertRepository.findById(concert1.getConcertId()).orElseThrow();
            Concert updated2 = concertRepository.findById(concert2.getConcertId()).orElseThrow();
            Concert updated3 = concertRepository.findById(concert3.getConcertId()).orElseThrow();

            assertThat(updated1.getAiSummary()).isEqualTo(MOCK_AI_SUMMARY);  // 처리됨
            assertThat(updated2.getAiSummary()).isNull();                      // 제외됨
            assertThat(updated3.getAiSummary()).isEqualTo("기존 요약");        // 스킵됨

            System.out.println("========== 복합 시나리오 결과 ==========");
            System.out.println("콘서트1 (처리대상): " + (updated1.getAiSummary() != null ? "처리됨" : "미처리"));
            System.out.println("콘서트2 (리뷰부족): " + (updated2.getAiSummary() != null ? "처리됨" : "미처리"));
            System.out.println("콘서트3 (스킵대상): " + updated3.getAiSummary());
        }
    }

    // ========== 헬퍼: 로그 출력 ==========

    private void printLog(String title, BatchExecutionLog log) {
        System.out.println("========== " + title + " ==========");
        System.out.println("Job: " + log.getJobName());
        System.out.println("상태: " + log.getStatus());
        System.out.println("전체: " + log.getTotalCount());
        System.out.println("성공: " + log.getSuccessCount());
        System.out.println("스킵: " + log.getSkipCount());
        System.out.println("실패: " + log.getFailCount());
        System.out.println("소요시간: " + log.getDurationMs() + "ms");
    }
}