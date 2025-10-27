package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 좌석 영구 선점 처리 결과 DTO
 * TTL 삭제 및 영구 선점 상태 변경 작업의 결과를 담는 record 클래스
 * 기능:
 * - 영구 선점 성공/실패 여부
 * - 상태 변경 전후 정보
 * - TTL 키 처리 결과
 * - 처리 소요 시간
 */
@Getter
@Builder
public class SeatLockResultDTO {

    /** 콘서트 ID */
    private final Long concertId;

    /** 좌석 ID */
    private final Long concertSeatId;

    /** 사용자 ID */
    private final Long userId;

    /** 영구 선점 시작 시간 */
    private final LocalDateTime lockStartTime;

    /** 영구 선점 완료 시간 */
    private final LocalDateTime lockEndTime;

    /** 이전 좌석 상태 */
    private final SeatStatusEnum previousStatus;

    /** 변경된 좌석 상태 */
    private final SeatStatusEnum newStatus;

    /** TTL 키 삭제 성공 여부 */
    private final boolean ttlKeyRemoved;

    /** 좌석 정보 */
    private final String seatInfo;

    /** 처리 성공 여부 */
    private final boolean success;

    /** 오류 메시지 (실패 시) */
    private final String errorMessage;

    /**
     * 영구 선점 처리 소요 시간 계산
     *
     * @return 소요 시간 (Duration)
     */
    public Duration getProcessingDuration() {
        if (lockStartTime == null || lockEndTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(lockStartTime, lockEndTime);
    }

    /**
     * 영구 선점 결과 요약 메시지 생성
     *
     * @return 결과 요약 문자열
     */
    public String getSummary() {
        if (!success) {
            return String.format("영구 선점 실패 - 콘서트: %d, 좌석: %d, 사용자: %d, 오류: %s",
                    concertId, concertSeatId, userId, errorMessage);
        }

        return String.format(
                "영구 선점 완료 - 콘서트: %d, 좌석: %d (%s), 사용자: %d, " +
                        "상태변경: %s→%s, TTL 삭제: %s, 소요시간: %dms",
                concertId, concertSeatId, seatInfo, userId,
                previousStatus, newStatus, ttlKeyRemoved ? "성공" : "실패",
                getProcessingDuration().toMillis()
        );
    }

    /**
     * 성공한 영구 선점 결과 생성
     *
     * @param concertId 콘서트 ID
     * @param concertSeatId 좌석 ID
     * @param userId 사용자 ID
     * @param previousStatus 이전 상태
     * @param newStatus 새 상태
     * @param ttlKeyRemoved TTL 키 삭제 여부
     * @param seatInfo 좌석 정보
     * @return 성공 결과
     */
    public static SeatLockResultDTO success(Long concertId, Long concertSeatId, Long userId,
                                            SeatStatusEnum previousStatus, SeatStatusEnum newStatus,
                                            boolean ttlKeyRemoved, String seatInfo) {
        LocalDateTime now = LocalDateTime.now();

        return SeatLockResultDTO.builder()
                .concertId(concertId)
                .concertSeatId(concertSeatId)
                .userId(userId)
                .lockStartTime(now)
                .lockEndTime(now)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .ttlKeyRemoved(ttlKeyRemoved)
                .seatInfo(seatInfo)
                .success(true)
                .build();
    }
}