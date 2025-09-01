package com.team03.ticketmon.booking.controller;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.dto.BookingCreateRequest;
import com.team03.ticketmon.booking.facade.BookingFacadeService;
import com.team03.ticketmon.booking.service.BookingService;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentResponseDto;
import com.team03.ticketmon.seat.dto.BulkSeatLockResultDTO;
import com.team03.ticketmon.seat.service.SeatLockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

/**
 * 예매(Booking)와 관련된 HTTP 요청을 처리하는 API 컨트롤러
 *
 * <p>
 * 이 컨트롤러는 클라이언트의 요청을 받아 서비스 계층에 전달하고,
 * 그 결과를 표준화된 응답 형식인 {@link SuccessResponse}로 감싸 반환
 * 모든 엔드포인트는 인증된 사용자만 접근 가능하다고 가정하며,
 * {@link AuthenticationPrincipal}을 통해 인증된 사용자 정보를 획득
 * </p>
 */
@Tag(name = "예약 API", description = "예매 생성, 취소 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingFacadeService bookingFacadeService; // Facade 주입
    private final BookingService bookingService;
    private final SeatLockService seatLockService;
    private static final long TIMEOUT_MS = 10_000L;


    @Operation(summary = "예매 생성 및 결제 준비", description = "좌석 영구 선점 후 예매를 생성하고, 즉시 결제에 필요한 정보를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "예매 정보 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 입력 (좌석 ID 누락 등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 콘서트 또는 좌석"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 선택되었거나 선점 정보가 유효하지 않은 좌석")
    })
    @PostMapping
    public ResponseEntity<SuccessResponse<PaymentExecutionResponse>> createBookingAndPreparePayment(
            @Valid @RequestBody BookingCreateRequest createRequest,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        log.info("예매 생성 및 결제 준비 시도. for user: {}", user.getUsername());

        try {
            // 1. 좌석 영구 선점 처리
            BulkSeatLockResultDTO lockResult = seatLockService.lockAllUserSeatsPermanently(
                    createRequest.getConcertId(), user.getUserId());

            // 영구 선점에 하나라도 실패한다면?!
            if (!lockResult.isAllSuccess()) {
                log.warn("좌석 영구 선점 실패: {}", lockResult.getErrorMessage());
                return ResponseEntity.badRequest().body(
                        SuccessResponse.of("좌석 영구 선점에 실패했습니다: " + lockResult.getErrorMessage(), null)
                );
            }

            // 2. 예매 생성 및 결제 정보 조회
            PaymentExecutionResponse responseDto = bookingFacadeService.createBookingAndInitiatePayment(createRequest,
                    user.getUserId());

            log.info("예매 생성 및 좌석 영구 선점 완료. user: {}, lockResult: {}",
                    user.getUsername(), lockResult.getSummary());

            return new ResponseEntity<>(SuccessResponse.of("예매 생성 및 결제 정보 조회가 완료되었습니다.", responseDto), HttpStatus.CREATED);

        } catch (Exception e) {
            log.error("예매 생성 중 예외 발생: user={}, concertId={}",
                    user.getUsername(), createRequest.getConcertId(), e);

            // 실패 시 좌석 복원 시도
            try {
                seatLockService.restoreAllUserSeatsWithCompensation(createRequest.getConcertId(), user.getUserId(), true);
                log.info("예매 실패 후 좌석 복원 완료");
            } catch (Exception restoreException) {
                log.error("좌석 복원 중 예외 발생", restoreException);
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    SuccessResponse.of("예매 처리 중 오류가 발생했습니다.", null)
            );
        }
    }

    /**
     * 예매를 취소. 이 요청은 결제 취소를 포함한 모든 과정을 동기적으로 처리
     *
     * @param bookingId 취소할 예매의 ID
     * @param user      현재 인증된 사용자 정보
     * @return 취소 성공 메시지
     */
    @Operation(summary = "예매 취소 (비동기)", description = "특정 예매를 취소 처리합니다. 성공 시 연동된 결제도 함께 취소됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자 (토큰 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "취소 권한 없음 (타인의 예매)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 예매"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태 충돌 (이미 취소 또는 완료된 예매)")
    })
    @PostMapping("/{bookingId}/cancel")
    public DeferredResult<ResponseEntity<SuccessResponse<Void>>> cancelBooking(
            @Parameter(description = "취소할 예매의 ID", required = true)
            @PathVariable Long bookingId,
            @AuthenticationPrincipal CustomUserDetails user) {
        log.info("예매 취소 시도 booking ID: {} for user: {}", bookingId, user);
        Long userId = user.getUserId();
        DeferredResult<ResponseEntity<SuccessResponse<Void>>> dr = new DeferredResult<>(TIMEOUT_MS);
        // 타임아웃 처리
        dr.onTimeout(() -> {
            log.warn("예약 취소 요청 타임아웃: bookingId={}", bookingId);
            dr.setResult(ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(
                    SuccessResponse.of("요청 처리 시간이 초과되었습니다.", null)
            ));
        });
        // 구독을 저장하여 필요시 취소 가능하도록
        var subscription =
                bookingFacadeService.cancelBookingAndPayment(bookingId, userId)
                        .doOnSuccess(v -> {
                            dr.setResult(ResponseEntity.ok(
                                    SuccessResponse.of("예매가 성공적으로 취소되었습니다.", null)
                            ));
                        })
                        .doOnError(e -> {
                            log.error("예매 취소 중 오류 발생: bookingId={}, error={}", bookingId, e.getMessage(), e);
                            HttpStatus status = (e instanceof BusinessException)
                                    ? HttpStatus.BAD_REQUEST
                                    : HttpStatus.INTERNAL_SERVER_ERROR;
                            dr.setResult(ResponseEntity.status(status).body(
                                    SuccessResponse.of("예매 취소 실패: " + e.getMessage(), null)
                            ));
                        })
                        .subscribe();
        // 클라이언트 연결 해제 시 구독 취소
        dr.onCompletion(() -> {
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        });
        return dr;
    }

    @Operation(summary = "예매 정보 조회", description = "bookingNumber로 예매 상세 정보를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 예매번호")
    })
    @GetMapping("/{bookingNumber}")
    public ResponseEntity<SuccessResponse<PaymentResponseDto>> getBooking(
            @PathVariable String bookingNumber,
            @AuthenticationPrincipal CustomUserDetails user   // 로그인 사용자 검증이 필요 없으면 빼셔도 됩니다.
    ) {
        Booking booking = bookingService.findByBookingNumberForUser(bookingNumber, user.getUserId());

        PaymentResponseDto dto = new PaymentResponseDto(booking);
        return ResponseEntity.ok(SuccessResponse.of("예매 정보 조회가 완료되었습니다.", dto));
    }

    @Operation(summary = "결제 취소 시 좌석 복원", description = "결제창 닫기 시 영구 선점된 좌석을 일반 선점 상태로 복원합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좌석 복원 성공"),
            @ApiResponse(responseCode = "400", description = "복원할 좌석이 없음"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @PostMapping("/concerts/{concertId}/seats/restore")
    public ResponseEntity<SuccessResponse<BulkSeatLockResultDTO>> restoreSeatsOnPaymentCancel(
            @Parameter(description = "콘서트 ID", required = true) @PathVariable Long concertId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {

        log.info("결제 취소 시 좌석 복원 요청: concertId={}, userId={}", concertId, user.getUserId());

        try {
            // 영구 선점된 좌석들을 일반 선점으로 복원 (TTL 5분 재설정)
            BulkSeatLockResultDTO restoreResult = seatLockService.restoreAllUserSeats(
                    concertId, user.getUserId(), true);

            if (restoreResult.isPartialSuccess()) {
                log.info("좌석 복원 완료: {}", restoreResult.getSummary());

                String message = restoreResult.isAllSuccess() ?
                        String.format("모든 좌석이 복원되었습니다. 5분 내 다시 결제해주세요. (%d석)", restoreResult.getSuccessCount()) :
                        String.format("일부 좌석이 복원되었습니다. (성공: %d석, 실패: %d석)",
                                restoreResult.getSuccessCount(), restoreResult.getFailureCount());

                return ResponseEntity.ok(SuccessResponse.of(message, restoreResult));
            } else {
                log.warn("좌석 복원 실패: {}", restoreResult.getErrorMessage());
                return ResponseEntity.badRequest().body(
                        SuccessResponse.of(
                                restoreResult.getErrorMessage() != null ?
                                        restoreResult.getErrorMessage() : "복원할 영구 선점 좌석이 없습니다.",
                                restoreResult)
                );
            }

        } catch (Exception e) {
            log.error("좌석 복원 중 예외 발생: concertId={}, userId={}", concertId, user.getUserId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    SuccessResponse.of("좌석 복원 처리 중 오류가 발생했습니다.", null)
            );
        }
    }

}
