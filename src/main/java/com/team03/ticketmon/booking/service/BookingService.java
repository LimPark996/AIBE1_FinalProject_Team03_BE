package com.team03.ticketmon.booking.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import com.team03.ticketmon.booking.dto.BookingCreateRequest;
import com.team03.ticketmon.booking.repository.BookingRepository;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.repository.PaymentRepository;
import com.team03.ticketmon.seat.exception.SeatReservationException;
import com.team03.ticketmon.seat.service.SeatCacheInitService;
import com.team03.ticketmon.seat.service.SeatStatusService;
import com.team03.ticketmon.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


/**
 * BookingService — 예매 생성/조회/취소 핵심 서비스
 *
 * 이 클래스가 하는 일:
 *   1. Redis 선점 상태를 전제로 PENDING_PAYMENT 예매 생성
 *   2. 선택한 좌석에 남아있는 미완성(결제 대기) 예매를 정리하고 좌석 상태 복구
 *   3. 예매 상세/목록 조회 및 소유자 검증
 *   4. 취소 가능 여부 검증 후 좌석 해제 · 상태 전이 · 히스토리 이관
 *   5. 15분 이상 방치된 PENDING 예매를 스케줄러로 자동 정리
 *
 * 상태 흐름:
 *   PENDING_PAYMENT → (결제 성공) → CONFIRMED → (사용자 취소) → CANCELED
 *   PENDING_PAYMENT → (15분 TTL 경과) → 자동 삭제
 *
 * 외부 연동:
 *   - SeatStatusService / SeatCacheInitService : Redis 좌석 상태 · 캐시
 *   - PaymentRepository : 정리 시 연결된 결제 엔티티 삭제
 *
 * 참고: 매개변수명은 concertSeatId 로 일관되게 맞춤.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ConcertRepository concertRepository;
    private final ConcertSeatRepository concertSeatRepository;
    private final SeatStatusService seatStatusService;
    private final EntityManager entityManager;
    private final SeatCacheInitService seatCacheInitService;
    private final PaymentRepository paymentRepository;

    /**
     * '결제 대기' 상태의 새로운 예매를 생성
     * 이 메서드는 Redis를 통해 좌석 선점이 완료된 후에 호출되어야 한다
     *
     * @param createDto 예매 생성에 필요한 데이터(콘서트 ID, 좌석 ID 목록)
     * @param userId    예매를 요청한 사용자의 ID
     * @return 생성된 Booking 엔티티
     * @throws BusinessException 콘서트 또는 좌석 정보를 찾을 수 없을 때
     */
    @Transactional
    public Booking createPendingBooking(BookingCreateRequest createDto, Long userId) {

        // 0. 유저 정보 조회 (유저가 존재하냐, 안하냐)
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 1. 콘서트 정보 조회
        Concert concert = concertRepository.findById(createDto.getConcertId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        // 2. 선택된 좌석 목록 및 총액 계산
        // 예매 생성 요청 데이터이기 때문에 concertSeat가 여러개일 수 있다.
        List<ConcertSeat> selectedSeats = concertSeatRepository.findAllById(createDto.getConcertSeatIds());
        // 만약에 예매 생성 요청 데이터에 있는 좌석 수와 실제로 불러온 좌석 수가 다르면 Exception 처리를 한다.
        if (selectedSeats.size() != createDto.getConcertSeatIds().size()) {
            throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
        }

        cleanupIncompleteBookingsForSeats(selectedSeats, userId);

        // 2-1. ✅ 수정: concertSeatId 사용으로 매개변수명 일관성 확보
        selectedSeats.forEach(seat ->
                validateSeatReservation(seat.getConcert().getConcertId(), seat.getConcertSeatId(), userId)
        );

        // 3. Ticket & Booking 생성
        Booking booking = Booking.createBooking(userId, concert, selectedSeats);

        // 4. Booking 저장
        Booking savedBooking = bookingRepository.save(booking);
        log.info("결제 대기 상태의 예매 생성 완료. Booking ID: {}", savedBooking.getBookingId());

        return savedBooking;
    }

    /**
     * 선택된 좌석들과 연관된 미완성 예매 데이터 정리
     */
    private void cleanupIncompleteBookingsForSeats(List<ConcertSeat> selectedSeats, Long userId) {
        Set<Long> processedBookingIds = new HashSet<>(); // 이미 처리한 Booking Id들

        // 1. 관련된 기존 예매들 정리
        for (ConcertSeat seat : selectedSeats) {
            if (seat.getTicket() != null) { // ConcertSeat(좌석)에는 Ticket(예매 티켓)과 연결이 되어있다.
                Booking existingBooking = seat.getTicket().getBooking(); // 예매 티켓은 Booking과 연결이 되어있다.
                // Booking이 존재하지만, 결제 대기 상태인 경우...
                if (existingBooking != null &&
                        existingBooking.getStatus() == BookingStatus.PENDING_PAYMENT &&
                        !processedBookingIds.contains(existingBooking.getBookingId())) {
                    // booking 내에 있는 여러 ticket들과 concertseat 관계를 해제한다.
                    // booking 내에 있는 여러 ticket들의 좌석 상태가 RESERVED/BOOKED 상태인지, 사용자 권한 검증 되었는지 확인
                    // 좌석 상태 Available로 변경 및 Redis 업데이트, TTL 키 삭제 그리고 이벤트 발행
                    // Redis 좌석 상태가 해제(Available)된 상태이면 booking과 연결된 payment 삭제
                    // booking 과 연계된 모든 Ticket들 삭제 -> db에서 booking 삭제
                    cleanupPendingBooking(existingBooking.getBookingId());
                    processedBookingIds.add(existingBooking.getBookingId()); // 위에서 처리한 bookingid를 HashSet에 추가한다.
                }
            }
        }

        // 2. 캐시 새로고침 및 선택된 모든 좌석 복원
        if (!processedBookingIds.isEmpty()) { // 만약 처리한 booking들이 비어있지 않는다면..
            Long concertId = selectedSeats.get(0).getConcert().getConcertId();
            refreshSeatCache(concertId);
            restoreSeatsToReserved(selectedSeats, userId);
            log.info("미완성 예매 정리 및 선택 좌석 복원 완료: cleanedBookings={}, restoredSeats={}",
                    processedBookingIds, selectedSeats.size());
        }
    }

    /**
     * 좌석들을 RESERVED 상태로 복원 (기존 reserveSeat 메서드 재사용)
     */
    private void restoreSeatsToReserved(List<ConcertSeat> seatsToRestore, Long userId) {
        log.info("복원 대상 좌석 목록: {}",
                seatsToRestore.stream()
                        .map(seat -> seat.getConcertSeatId())
                        .collect(Collectors.toList()));

        for (ConcertSeat seat : seatsToRestore) {
            log.info("좌석 복원 시작: seatId={}", seat.getConcertSeatId());
            try {
                Long concertId = seat.getConcert().getConcertId();
                Long concertSeatId = seat.getConcertSeatId();
                String seatInfo = seat.getSeatInfo();

                // 기존 reserveSeat 메서드 재사용 - AVAILABLE -> RESERVED 처리
                seatStatusService.reserveSeat(concertId, concertSeatId, userId, seatInfo);

                log.info("좌석 RESERVED 상태로 복원: concertId={}, concertSeatId={}, userId={}",
                        concertId, concertSeatId, userId);

            } catch (Exception e) {
                log.error("좌석 복원 실패: seatId={}, userId={}",
                        seat.getConcertSeatId(), userId, e);
            }
        }
    }

    /**
     * 특정 콘서트에 대한 모든 좌석 캐시 새로고침
     */
    private void refreshSeatCache(Long concertId) {
        try {
            // 특정 콘서트에 존재하는 모든 좌석 캐시 삭제
            seatCacheInitService.clearSeatCache(concertId);

            // DB 기반 캐시 재초기화
            seatCacheInitService.initializeSeatCacheFromDB(concertId);

            log.info("좌석 캐시 새로고침 완료: concertId={}", concertId);
        } catch (Exception e) {
            log.error("좌석 캐시 새로고침 실패: concertId={}", concertId, e);
            // 캐시 실패는 예매 진행에 치명적이지 않으므로 예외를 던지지 않음
        }
    }

    /**
     * PENDING_PAYMENT 상태의 예매를 완전히 정리한다.
     * 좌석-티켓 연관관계 해제 → Redis 좌석 해제 → Payment 삭제 → Ticket/Booking 삭제 순으로 진행한다.
     * (자체 트랜잭션으로 실행되며 PENDING 외 상태는 안전하게 무시한다.)
     */
    @Transactional
    public void cleanupPendingBooking(Long bookingId) {
        try {
            // bookingId로 booking 정보를 찾자
            Booking booking = bookingRepository.findById(bookingId).orElse(null);
            // 못찾는다면 -> 정리 대상 예매가 존재하지 않는 것임
            if (booking == null) {
                log.debug("정리 대상 예매가 존재하지 않음: bookingId={}", bookingId);
                return;
            }
            // 찾았으나, 결제 대기 상태가 아니라면 -> warn을 띄우고 끝냄
            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
                log.warn("PENDING 상태가 아닌 예매 정리 시도: bookingId={}, status={}",
                        bookingId, booking.getStatus());
                return;
            }
            // 찾았고, 결제 대기 상태라면
            Long concertId = booking.getConcert().getConcertId(); // 해당 예매의 콘서트 id를 불러서 저장함
            Long userId = booking.getUserId(); // 해당 예매를 한 사용자의 아이디를 저장함

            // 1. ConcertSeat-Ticket 연관관계 해제
            // 예매는 한번에 여러개의 티켓 점유가 가능하다. -> 예매된 여러개의 티켓과 콘서트 좌석 정보의 연관관계 해제
            booking.getTickets().forEach(ticket -> {
                ConcertSeat concertSeat = ticket.getConcertSeat(); // 하나의 티켓에는 하나의 콘서트 좌석! 티켓에 할당된 콘서트 좌석 정보를 가져온다.
                // 콘서트 좌석 정보가 존재하고, 콘서트 좌석 정보의 티켓이 현재 티켓과 동일하다면
                if (concertSeat != null && concertSeat.getTicket() == ticket) {
                    concertSeat.releaseTicket(); // 콘서트 좌석 정보와 연결된 티켓 삭제
                    log.debug("ConcertSeat-Ticket 연관관계 해제: seatId={}",
                            concertSeat.getConcertSeatId());
                }
            });

            // 2. Redis 좌석 상태 해제
            booking.getTickets().forEach(ticket -> {
                try { //ConcertSeat -> ConcertSeatId 추출
                    Long seatId = ticket.getConcertSeat().getConcertSeatId();
                    // 좌석 상태 해제 가능한지 (RESERVED/BOOKED 상태인지, 사용자 권한 검증 되었는지) 확인 후,
                    // 좌석 상태 Available로 변경 및 Redis 업데이트, TTL 키 삭제 그리고 이벤트 발행
                    seatStatusService.releaseSeat(concertId, seatId, userId);
                } catch (Exception e) {
                    log.warn("Redis 좌석 해제 실패: seatId={}",
                            ticket.getConcertSeat().getConcertSeatId(), e);
                }
            });

            // 3. Redis 좌석 상태가 해제(Available)된 상태이다. -> booking과 연결된 Payment 삭제
            if (booking.getPayment() != null) {
                paymentRepository.delete(booking.getPayment());
            }

            // 4. Booking 및 Ticket 삭제
            booking.removeAllTickets(); // booking과 연계된 모든 Ticket들을 삭제한다. 그리고 booking의 Tickets라는 리스트를 빈 리스트로 반환한다.
            bookingRepository.delete(booking); // booking 이라는 정보를 데이터에서 삭제한다.

            log.info("PENDING 예매 완전 정리 완료: bookingId={}", bookingId);

        } catch (Exception e) {
            log.error("PENDING 예매 정리 중 오류: bookingId={}", bookingId, e);
        }
    }

    /**
     * 특정 사용자의 전체 예매 목록을 조회한다. (콘서트/티켓/좌석 fetch join)
     */
    @Transactional
    public List<Booking> findBookingList(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return bookingRepository.findByUserId(userId);
    }

    /**
     * 예매번호로 예매 상세를 조회한다. (사용자 존재 여부 선검증)
     */
    @Transactional
    public Optional<Booking> findBookingDetail(Long userId, String bookingNumber) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return bookingRepository.findByBookingNumber(bookingNumber);
    }

    /**
     * 결제 취소가 완료된 예매의 내부 상태를 마무리한다.
     * 좌석(Redis/ConcertSeat) 해제 → Booking·Payment 상태 CANCELED 전이 →
     * 히스토리 이관 → Ticket 정리 순으로 처리한다. (bookingId 로 세션 내 재로드)
     */
    @Transactional
    public void finalizeCancellation(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BOOKING_NOT_FOUND,
                        "존재하지 않는 예매 ID: " + bookingId));

        Long concertId = booking.getConcert().getConcertId();

        // 이제 tickets 컬렉션이 session 안에서 안전하게 초기화됩니다.
        // [좌석 반환] 예매된 좌석들을 다시 'AVAILABLE' 상태로 변경하는 로직 추가
        booking.getTickets().forEach(ticket -> {
            try {
                Long seatId = ticket.getConcertSeat().getConcertSeatId();
                seatStatusService.releaseSeat(concertId, seatId, booking.getUserId());
                ConcertSeat concertSeat = ticket.getConcertSeat();
                if (concertSeat != null) {
                    concertSeat.releaseTicket(); // ConcertSeat의 ticket 필드를 null로 설정
                    ticket.releaseConcertSeat();  // Ticket의 concertSeat 필드를 null로 설정
                }
            } catch (SeatReservationException e) {
                log.warn("[Cancel] 좌석 해제 스킵: seatId={}, 이유={}",
                        ticket.getConcertSeat().getConcertSeatId(),
                        e.getMessage());
            }
        });

        // 예약 상태를 반드시 CANCELED로 변경하고 저장
        booking.cancel();
        booking.getPayment().cancel();

        // 히스토리 테이블로 이관하는 로직 호출
        archiveBookingAndTickets(booking);

        booking.removeAllTickets();

        bookingRepository.save(booking);
        log.info("finalizeCancellationById 완료: bookingId={}", bookingId);
    }


    /**
     * 예매 취소 요청의 유효성을 검사
     * 취소할 예매 엔티티를 반환
     *
     * @param bookingId 검사할 예매 ID
     * @param userId    요청한 사용자 ID
     * @return 검증이 완료된 Booking 엔티티
     * @throws BusinessException 유효성 검사 실패 시 (소유권, 상태, 취소 기간 등)
     */
    @Transactional(readOnly = true)
    public Booking validateCancellableBooking(Long bookingId, Long userId) {
        // 1. 예매 정보 조회
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        // 2. 예매 취소 권한 확인
        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 3. 이미 취소되었거나 완료된 예매인지 확인
        if (booking.getStatus() == BookingStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED_BOOKING);
        }

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_COMPLETED_BOOKING);
        }

        // TODO: [취소 정책] 상세한 비즈니스 규칙을 추가 (예: 공연 1일 전까지 가능)(현재 하드코딩)
        // 4. 취소 정책 검증 (예: 공연 시작일 하루 전까지만 가능)
        if (booking.getConcert().getConcertDate().isBefore(LocalDate.now().plusDays(1))) {
            throw new BusinessException(ErrorCode.CANCELLATION_PERIOD_EXPIRED);
        }

        return booking;
    }

    /**
     * ✅ 수정된 좌석 선점 검증 메서드 - 매개변수명 일관성 확보
     * Redis의 좌석 상태를 확인하여, 해당 좌석이 주어진 사용자에 의해 유효하게 선점되었는지 검증합니다.
     */
    private void validateSeatReservation(Long concertId, Long concertSeatId, Long userId) {
        seatStatusService.getSeatStatus(concertId, concertSeatId)
                .filter(status -> status.isReserved() && userId.equals(status.getUserId()) && !status.isExpired())
                .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN,
                        "좌석 선점 정보가 유효하지 않습니다. ConcertSeat ID: " + concertSeatId)); // ✅ 수정: 메시지 업데이트
    }

    /**
     * [가짜 메서드] 예매와 티켓 정보를 히스토리 테이블로 이관
     * 실제 서비스에서 이 부분에 데이터 이관을 고려함
     *
     * @param booking 이관할 예매 정보
     */
    private void archiveBookingAndTickets(Booking booking) {

        // [미래 구현] 아래 로직을 실제 히스토리 DB에 맞게 구현

        // 1. 변경된 상태가 반영된 객체를 기반으로 History 객체를 생성합니다.
        //    BookingHistory bookingHistory = BookingHistory.from(booking);

        // 2. History DB에 저장합니다.
        //    bookingHistoryRepository.save(bookingHistory);
        //    ... (Ticket 히스토리 저장 로직) ...

        log.info("[시뮬레이션] Booking ID {} 및 관련 Ticket 정보를 히스토리 테이블로 이관 완료.", booking.getBookingId());
    }

    /**
     * 1분마다 실행되어, 결제 대기 상태로 15분 이상 방치된 예매를 자동 취소 및 데이터 정리합니다.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredPendingBookings() {
        LocalDateTime expirationTime = LocalDateTime.now().minusMinutes(15);
        log.info("취소 기준 시각(expirationTime): {}", expirationTime);
        List<Booking> expiredBookings = bookingRepository.findExpiredPendingBookings(expirationTime);

        for (Booking booking : expiredBookings) {
            // Redis 좌석 해제
            Long concertId = booking.getConcert().getConcertId();
            booking.getTickets().forEach(ticket -> {
                Long concertSeatId = ticket.getConcertSeat().getConcertSeatId();
                seatStatusService.forceReleaseSeat(concertId, concertSeatId);
            });

            // 아카이빙 스텁 (추후 구현)
            archiveBookingAndTickets(booking);

            // Hibernate orphanRemoval을 통해 자식 티켓 먼저 삭제
            booking.removeAllTickets();
            bookingRepository.delete(booking);

            log.info("자동 취소 처리된 예매: {}", booking.getBookingNumber());
        }
    }

    /**
     * bookingNumber로 예매를 조회하고,
     * 요청한 userId와 소유자가 다르면 예외를 던집니다.
     *
     * @param bookingNumber 예매 조회 키
     * @param userId        요청한 사용자 ID
     * @return 조회된 Booking 엔티티
     */
    @Transactional(readOnly = true)
    public Booking findByBookingNumberForUser(String bookingNumber, Long userId) {
        Booking booking = bookingRepository.findByBookingNumber(bookingNumber)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "존재하지 않는 예매번호입니다: " + bookingNumber
                ));
        if (!booking.getUserId().equals(userId)) {
            throw new AccessDeniedException("본인의 예매 내역만 조회할 수 있습니다.");
        }
        return booking;
    }
}
