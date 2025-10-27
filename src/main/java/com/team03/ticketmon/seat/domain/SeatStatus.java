package com.team03.ticketmon.seat.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.LocalDateTime;

/**
 * Redis Hash 기반 좌석 상태 엔티티
 * - 키: seat:status:{concertId}
 * - Hash 필드: seatId를 키로 하는 Hash 구조
 */
@Getter
@Builder
@NoArgsConstructor  // Spring Data Redis 역직렬화를 위한 기본 생성자
@AllArgsConstructor // @Builder와 함께 사용하기 위한 전체 인수 생성자
@RedisHash(value = "seat:status", timeToLive = 3600) // 1시간 TTL
public class SeatStatus {

    @Id
    private String id; // concertId-seatId 형태로 구성

    private Long concertId;      // 콘서트 ID
    private Long seatId;         // 좌석 ID
    private SeatStatusEnum status; // 좌석 상태
    private Long userId;         // 선점한 사용자 ID (선점 시에만)
    private LocalDateTime reservedAt; // 선점 시간
    private LocalDateTime expiresAt;  // 선점 만료 시간
    private String seatInfo;     // 좌석 정보 (A-1, B-15 등)

    public enum SeatStatusEnum {
        AVAILABLE,    // 예매 가능
        RESERVED,     // 임시 선점 (5분)
        BOOKED,       // 예매 완료
        UNAVAILABLE   // 예매 불가
    }

    // 좌석이 선점 가능한지 확인
    public boolean isAvailable() {
        return status == SeatStatusEnum.AVAILABLE;
    }

    // 좌석이 현재 선점 중인지 확인
    public boolean isReserved() {
        return status == SeatStatusEnum.RESERVED;
    }

    // 좌석 선점이 만료되었는지 확인
    // 만료된 경우 객체 내부 상태를 일관성 있게 유지하기 위해 읽기 전용 확인만 수행
    // 중요: 이 메서드는 상태 변경을 수행하지 않습니다.
    // 만료된 선점을 처리하려면 SeatStatusService.releaseSeat()을 호출해야 합니다.
    public boolean isExpired() {
        return isReserved() && expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    // 선점 만료까지 남은 시간(초) 계산
    // 만료된 경우(만료된 선점 처리 이후) 0을 반환
    public long getRemainingSeconds() {
        if (!isReserved() || expiresAt == null) {
            return 0L;
        }
        long seconds = java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        return Math.max(0L, seconds); // 음수 방지
    }
}