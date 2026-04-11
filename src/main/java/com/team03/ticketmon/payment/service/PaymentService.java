package com.team03.ticketmon.payment.service;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import com.team03.ticketmon.booking.repository.BookingRepository;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.domain.entity.Payment;
import com.team03.ticketmon.payment.domain.entity.PaymentCancelHistory;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentHistoryDto;
import com.team03.ticketmon.payment.repository.PaymentCancelHistoryRepository;
import com.team03.ticketmon.payment.repository.PaymentRepository;
import com.team03.ticketmon.seat.service.SeatStatusService;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PaymentService — 결제 처리 핵심 서비스
 *
 * 이 클래스가 하는 일:
 *   1. 예매(Booking)에 대한 결제 요청 생성 및 토스페이먼츠 결제창 URL 전달
 *   2. 토스페이먼츠 "승인 API" 호출 → 결제 상태 PENDING → DONE 전이
 *   3. 결제 취소/환불 시 토스페이먼츠 "취소 API" 호출 및 취소 이력 저장
 *   4. 웹훅 수신 기반 결제 상태 동기화(상태 전이 방어 로직 포함)
 *   5. 결제 완료 시 좌석 상태(BOOKED) 전환까지 일괄 수행
 *
 * 동작 흐름:
 *   - Booking(PENDING_PAYMENT) → initiatePayment → orderId 발급 → 프론트 결제창
 *   - 사용자 결제 완료 → /success 콜백 → confirmPayment → 토스 승인 API → Booking CONFIRMED, 좌석 BOOKED
 *   - 실패/취소 → handlePaymentFailure / cancelPayment → 상태 복구 및 Booking 취소
 *   - 비동기 서버 간 이벤트 → updatePaymentStatusByWebhook 로 상태 동기화
 *
 * 외부 API:
 *   - Toss Confirm: POST https://api.tosspayments.com/v1/payments/confirm
 *   - Toss Cancel : POST https://api.tosspayments.com/v1/payments/{paymentKey}/cancel
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCancelHistoryRepository paymentCancelHistoryRepository;
    private final TossPaymentsProperties tossPaymentsProperties;
    private final AppProperties appProperties;
    private final WebClient webClient;
    private final UserRepository userRepository;
    private final SeatStatusService seatStatusService;

    /**
     * 결제 요청을 초기화한다. 예매 소유자/상태 검증 후 orderId를 발급하여
     * 프론트가 토스페이먼츠 결제창을 띄우는 데 필요한 정보를 반환한다.
     * 기존 PENDING 결제가 있으면 재사용한다. (트랜잭션 쓰기)
     */
    @Transactional
    public PaymentExecutionResponse initiatePayment(Booking booking, Long currentUserId) {
        if (booking == null) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        if (!booking.getUserId().equals(currentUserId)) {
            log.warn("사용자 {}가 본인 소유가 아닌 예매(ID:{}) 결제를 시도했습니다.", currentUserId, booking.getBookingId());
            throw new AccessDeniedException("본인의 예매만 결제할 수 있습니다.");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATUS_FOR_PAYMENT);
        }
        if (booking.getConcert() == null) {
            throw new IllegalStateException("예매에 연결된 콘서트 정보가 없습니다. Booking ID: " + booking.getBookingId());
        }

        Payment paymentToUse = paymentRepository.findByBooking(booking)
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .orElseGet(() -> {
                    log.info("신규 결제 정보를 생성합니다. bookingNumber: {}", booking.getBookingNumber());
                    String orderId = UUID.randomUUID().toString();
                    Payment newPayment = Payment.builder()
                            .booking(booking)
                            .userId(booking.getUserId())
                            .orderId(orderId)
                            .amount(booking.getTotalAmount())
                            .build();
                    booking.setPayment(newPayment);
                    return paymentRepository.save(newPayment);
                });

        String customerName = userRepository.findById(currentUserId)
                .map(user -> user.getNickname())
                .orElse("사용자 " + currentUserId);

        return PaymentExecutionResponse.builder()
                .orderId(paymentToUse.getOrderId())
                .bookingNumber(booking.getBookingNumber())
                .orderName(booking.getConcert().getTitle())
                .amount(booking.getTotalAmount())
                .customerName(customerName)
                .clientKey(tossPaymentsProperties.clientKey())
                .successUrl(appProperties.baseUrl() + "/api/v1/payments/success")
                .failUrl(appProperties.baseUrl() + "/api/v1/payments/fail")
                .build();
    }

    /**
     * Payment를 저장하고 연결된 Booking을 CONFIRMED로 확정한다.
     * (영속성 컨텍스트가 유효한 범위 내에서 Booking.confirm 호출)
     */
    @Transactional
    public void savePayment(Payment payment) {
        paymentRepository.save(payment);
        bookingRepository.findById(payment.getBooking().getBookingId())
                .ifPresent(b -> b.confirm());   // 이 시점엔 세션이 열려 있어 안전
    }


    /**
     * 결제 승인 처리(비동기). DB에서 Payment 로드 → 상태/금액 검증 →
     * 토스페이먼츠 승인 API 호출 → 응답 검증 → Payment DONE / Booking CONFIRMED /
     * 좌석 BOOKED 전이까지 일괄 수행한다. 블로킹 작업은 boundedElastic 풀에서 실행.
     */
    @Transactional
    public Mono<Void> confirmPayment(PaymentConfirmRequest req) {
        // 1) DB에서 Payment 로드 & 검증
        return Mono.fromCallable(() ->
                        paymentRepository.findByOrderId(req.getOrderId())
                                .orElseThrow(() -> new BusinessException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "존재하지 않는 주문 ID: " + req.getOrderId()))
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(payment -> {
                    // 상태 검증
                    if (payment.getStatus() != PaymentStatus.PENDING) {
                        return Mono.error(new BusinessException(
                                ErrorCode.ALREADY_PROCESSED_PAYMENT,
                                "이미 처리된 결제입니다."));
                    }
                    // 금액 검증
                    if (payment.getAmount().compareTo(req.getAmount()) != 0) {
                        payment.fail();
                        return Mono.error(new BusinessException(
                                ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                                "결제 금액이 일치하지 않습니다."));
                    }
                    return Mono.just(payment);
                })
                // 2) Toss 승인 API 호출
                .flatMap(payment -> {
                    String rawKey = tossPaymentsProperties.secretKey() + ":";
                    String encodedKey = Base64.getEncoder()
                            .encodeToString(rawKey.getBytes(StandardCharsets.UTF_8));
                    return callTossConfirmApi(req, encodedKey, req.getOrderId())
                            .map(resp -> Tuples.of(payment, resp));
                })
                // 3) 응답 검사 & 저장
                .flatMap(tuple -> {
                    Payment payment = tuple.getT1();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resp = (Map<String, Object>) tuple.getT2();

                    // Toss 응답 검증
                    String status = (String) resp.get("status");
                    if (!"DONE".equals(status)) {
                        payment.fail();
                        return Mono.error(new BusinessException(
                                ErrorCode.PAYMENT_VALIDATION_FAILED,
                                "Toss 승인 상태가 DONE이 아닙니다: " + status));
                    }

                    // 파싱
                    LocalDateTime approvedAt = parseDateTime(resp.get("approvedAt"));

                    // ① 클라이언트가 선택한 결제수단을 먼저 저장
                    payment.setPaymentMethod(req.getOriginalMethod());

                    // 4) 영속성 작업
                    return Mono.fromRunnable(() -> {
                                // 결제 상태 갱신
                                payment.complete(req.getPaymentKey(), approvedAt);
                                paymentRepository.save(payment);

                                // 예매 상태 갱신 (concert + tickets 함께 로딩)
                                Long bookingId = payment.getBooking().getBookingId();
                                Booking booking = bookingRepository
                                        .findWithConcertAndTicketsById(bookingId)
                                        .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "예매를 찾을 수 없습니다: " + bookingId
                                        ));
                                booking.confirm();
                                bookingRepository.save(booking);

                                // 좌석 상태 BOOKED로 전환
                                Long concertId = booking.getConcert().getConcertId();
                                List<Long> failedSeats = new ArrayList<>();
                                booking.getTickets().forEach(ticket -> {
                                    try {
                                        seatStatusService.bookSeat(
                                                concertId,
                                                ticket.getConcertSeat().getConcertSeatId()
                                        );
                                    } catch (Exception e) {
                                        log.error("좌석 BOOKED 처리 실패: ticketId={}, error={}",
                                                ticket.getTicketId(), e.getMessage(), e);
                                        failedSeats.add(ticket.getConcertSeat().getConcertSeatId());
                                    }
                                });
                                if (!failedSeats.isEmpty()) {
                                    // 일부 좌석 처리 실패 시 보상 처리 또는 예외 발생
                                    throw new BusinessException(ErrorCode.SEAT_BOOKING_FAILED,
                                            "일부 좌석 예약에 실패했습니다: " + failedSeats);
                                }
                            })

                            .subscribeOn(Schedulers.boundedElastic());
                })
                .then();  // Mono<Void> 반환
    }

    /**
     * 토스페이먼츠 실패 리다이렉트 처리. PENDING 결제만 FAILED로 전이하고
     * 연결된 Booking도 취소 상태로 돌린다.
     */
    @Transactional
    public void handlePaymentFailure(String orderId, String errorCode, String errorMessage) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.fail();
                payment.getBooking().cancel();
                log.info("결제 실패 상태로 변경 완료: orderId={}", orderId);
            }
        });
    }

    /**
     * 결제 취소(비동기). 소유권/상태 검증 후 토스페이먼츠 취소 API를 호출하고,
     * 응답의 cancels 정보를 PaymentCancelHistory로 저장한다.
     * 검증/DB 반영은 boundedElastic 풀에서 수행한다.
     */
    @Transactional
    public Mono<Void> cancelPayment(Booking booking,
                                    PaymentCancelRequest cancelRequest,
                                    Long currentUserId) {
        return Mono.defer(() -> {
                    // 1) 검증 로직은 기존 메서드 그대로
                    if (booking == null) {
                        return Mono.error(new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
                    }
                    Payment payment = booking.getPayment();
                    if (payment == null) {
                        log.warn("예매(ID:{})에 결제 정보가 없어 취소를 스킵합니다.", booking.getBookingId());
                        return Mono.empty();
                    }
                    if (!payment.getUserId().equals(currentUserId)) {
                        log.warn("사용자 {}가 본인 결제(orderId:{})가 아닌 결제 취소를 시도했습니다.",
                                currentUserId, payment.getOrderId());
                        return Mono.error(new AccessDeniedException("본인의 결제만 취소할 수 있습니다."));
                    }
                    if (payment.getStatus() != PaymentStatus.DONE &&
                            payment.getStatus() != PaymentStatus.PARTIAL_CANCELED) {
                        log.info("취소할 수 없는 결제 상태({})입니다: orderId={}",
                                payment.getStatus(), payment.getOrderId());
                        return Mono.empty();
                    }

                    // 2) Toss 취소 API 호출 (논블록)
                    String raw = tossPaymentsProperties.secretKey() + ":";
                    String encodedKey = Base64.getEncoder()
                            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                    return callTossCancelApi(payment.getPaymentKey(),
                            cancelRequest.getCancelReason(),
                            encodedKey)
                            // 3) 응답 받으면 블로킹 풀에서 DB 업데이트
                            .flatMap(tossResponse -> Mono.fromRunnable(() -> {
                                // 기존 동기 로직 그대로
                                payment.cancel();
                                List<Map<String, Object>> cancels =
                                        (List<Map<String, Object>>) tossResponse.get("cancels");
                                if (cancels != null && !cancels.isEmpty()) {
                                    Map<String, Object> last = cancels.get(cancels.size() - 1);
                                    PaymentCancelHistory hist = PaymentCancelHistory.builder()
                                            .payment(payment)
                                            .transactionKey((String) last.get("transactionKey"))
                                            .cancelAmount(new BigDecimal(last.get("cancelAmount").toString()))
                                            .cancelReason((String) last.get("cancelReason"))
                                            .canceledAt(parseDateTime(last.get("canceledAt")))
                                            .build();
                                    paymentCancelHistoryRepository.save(hist);
                                }
                                log.info("결제 취소 완료 (async): orderId={}", payment.getOrderId());
                            }).subscribeOn(Schedulers.boundedElastic()))
                            // 4) Mono<Void> 로 끝맺음
                            .then();
                })
                // 전체를 블로킹 풀에서 시작
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 특정 사용자의 전체 결제 내역을 조회한다. (읽기 전용)
     */
    @Transactional(readOnly = true)
    public List<PaymentHistoryDto> getPaymentHistoryByUserId(Long userId) {
        return paymentRepository.findByUserId(userId)
                .stream()
                .map(PaymentHistoryDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 💡 [핵심 수정] 웹훅을 통해 결제 상태를 안전하게 업데이트합니다.
     *
     * @param orderId 업데이트할 주문 ID
     * @param status  새로운 결제 상태 문자열 (예: "DONE", "CANCELED")
     */
    @Transactional
    public void updatePaymentStatusByWebhook(String orderId, String status) {
        log.info("웹훅을 통한 결제 상태 업데이트 시도: orderId={}, status={}", orderId, status);

        // 1. 💡 [수정] DB에서 Payment와 연관된 Booking을 함께 조회 (N+1 문제 방지 및 상태 변경 용이)
        Payment payment = paymentRepository.findWithBookingByOrderId(orderId) // Repository에 메서드 추가 필요
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "웹훅 처리: 결제 정보를 찾을 수 없습니다. orderId=" + orderId));

        PaymentStatus newStatus;
        try {
            // 2. 💡 [수정] 처리할 수 없는 상태값이 들어올 경우에 대비한 예외 처리
            newStatus = PaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("웹훅 처리: 지원하지 않는 결제 상태값({})을 수신하여 처리를 건너뜁니다. orderId={}", status, orderId);
            return; // 500 에러를 발생시키지 않고 정상 종료
        }

        // 3. 💡 [수정] 이미 최종 상태(DONE, CANCELED 등)이거나, 요청된 상태와 현재 상태가 같으면 처리하지 않음
        if (payment.getStatus().isFinalState() || payment.getStatus() == newStatus) {
            log.info("웹훅 처리: 이미 최종 상태이거나 상태 변경이 불필요하여 건너뜁니다. orderId={}, 현재상태={}, 요청상태={}",
                    orderId, payment.getStatus(), newStatus);
            return;
        }

        // 4. 💡 [수정] 상태 전이(State Transition) 로직 강화
        switch (newStatus) {
            case DONE:
                // 오직 PENDING 상태일 때만 DONE으로 변경 가능
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.complete(payment.getPaymentKey(), LocalDateTime.now());
                    payment.getBooking().confirm();

                    // 웹훅으로 결제 완료 후 예매의 모든 좌석을 BOOKED 상태로 변경
                    payment.getBooking().getTickets().forEach(ticket -> {
                        try {
                            seatStatusService.bookSeat(
                                    payment.getBooking().getConcert().getConcertId(),
                                    ticket.getConcertSeat().getConcertSeatId()
                            );
                            log.debug("웹훅: 좌석 상태 BOOKED로 변경 완료: concertId={}, seatId={}",
                                    payment.getBooking().getConcert().getConcertId(),
                                    ticket.getConcertSeat().getConcertSeatId());
                        } catch (Exception e) {
                            log.error("웹훅: 좌석 상태 BOOKED 변경 실패: concertId={}, seatId={}, error={}",
                                    payment.getBooking().getConcert().getConcertId(),
                                    ticket.getConcertSeat().getConcertSeatId(), e.getMessage());
                        }
                    });

                    log.info("웹훅: 결제 {} 상태 PENDING -> DONE 업데이트 완료", orderId);
                } else {
                    log.warn("웹훅: 잘못된 상태 전이 시도(DONE). orderId={}, 현재상태={}", orderId, payment.getStatus());
                }
                break;

            case CANCELED:
                // DONE 또는 PENDING 상태에서 CANCELED로 변경 가능
                if (payment.getStatus() == PaymentStatus.DONE || payment.getStatus() == PaymentStatus.PENDING) {
                    payment.cancel();
                    payment.getBooking().cancel();
                    log.info("웹훅: 결제 {} 상태 {} -> CANCELED 업데이트 완료", orderId, payment.getStatus());
                } else {
                    log.warn("웹훅: 잘못된 상태 전이 시도(CANCELED). orderId={}, 현재상태={}", orderId, payment.getStatus());
                }
                break;

            case FAILED:
            case EXPIRED:
                // 오직 PENDING 상태일 때만 FAILED 또는 EXPIRED로 변경 가능
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.fail(); // FAILED, EXPIRED 모두 fail() 메서드로 처리
                    payment.getBooking().cancel();
                    log.info("웹훅: 결제 {} 상태 PENDING -> {} 업데이트 완료", orderId, newStatus);
                } else {
                    log.warn("웹훅: 잘못된 상태 전이 시도({}). orderId={}, 현재상태={}", newStatus, orderId, payment.getStatus());
                }
                break;

            default:
                log.warn("웹훅 처리: 정의되지 않은 상태({})에 대한 로직이 없습니다. orderId={}", newStatus, orderId);
                break;
        }
    }

    /**
     * 💡 [복원 및 수정] 토스페이먼츠의 "결제 승인 API"를 호출하는 private 헬퍼 메서드
     *
     * @param confirmRequest   결제 승인 요청 DTO
     * @param encodedSecretKey 인코딩된 시크릿 키
     * @param idempotencyKey   멱등성 키
     * @return 토스페이먼츠 API 응답을 담은 Mono<Map>
     */
    private Mono<Map<String, Object>> callTossConfirmApi(PaymentConfirmRequest confirmRequest, String encodedSecretKey,
                                                         String idempotencyKey) {
        return webClient.post()
                .uri("https://api.tosspayments.com/v1/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "paymentKey", confirmRequest.getPaymentKey(),
                        "orderId", confirmRequest.getOrderId(),
                        "amount", confirmRequest.getAmount()
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(
                                new BusinessException(ErrorCode.TOSS_API_ERROR, "토스페이먼츠 승인 API 호출 실패: " + errorBody))))
                .bodyToMono(new ParameterizedTypeReference<>() {
                });
    }

    private Mono<Map<String, Object>> callTossCancelApi(String paymentKey, String cancelReason,
                                                        String encodedSecretKey) {
        return webClient.post()
                .uri("https://api.tosspayments.com/v1/payments/{paymentKey}/cancel", paymentKey)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("cancelReason", cancelReason))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(
                                new BusinessException(ErrorCode.TOSS_API_ERROR, "토스페이먼츠 취소 API 호출 실패: " + errorBody))))
                .bodyToMono(new ParameterizedTypeReference<>() {
                }); // 💡 컴파일 에러 해결
    }

    private LocalDateTime parseDateTime(Object dateTimeObj) {
        String dateTimeStr = dateTimeObj.toString();
        try {
            // ① 오프셋 포함 포맷(예: 2025-07-14T03:00:50+09:00) 파싱
            return OffsetDateTime.parse(
                    dateTimeStr,
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME
            ).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            // ② 순수 LocalDateTime 포맷(예: 2025-07-14T03:00:50)으로 다시 시도
            return LocalDateTime.parse(
                    dateTimeStr,
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );
        }
    }

    /**
     * 주문 ID로 결제 정보를 조회하여 연결된 예매번호를 반환합니다.
     *
     * @param orderId TossPayments 주문 ID
     * @return 예매번호
     * @throws BusinessException 결제 정보나 예매가 없을 때
     */
    public String getBookingNumberByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "존재하지 않는 주문 ID 입니다: " + orderId));
        if (payment.getBooking() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "결제에 연결된 예매 정보가 없습니다. orderId=" + orderId);
        }
        return payment.getBooking().getBookingNumber();
    }
}
