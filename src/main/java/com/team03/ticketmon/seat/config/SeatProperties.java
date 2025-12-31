package com.team03.ticketmon.seat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 좌석 관리 시스템 설정값 관리
 * - 하드코딩된 설정값들을 외부 설정으로 분리
 * - application.yml에서 설정 가능
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "seat")
public class SeatProperties {

    /**
     * 좌석 선점 관련 설정
     */
    private Reservation reservation = new Reservation();

    /**
     * 폴링 관련 설정
     */
    private Polling polling = new Polling();

    /**
     * 세션 관리 설정
     */
    private Session session = new Session();

    /**
     * 분산 락 설정
     */
    private Lock lock = new Lock();

    /**
     * 캐시 설정
     */
    private Cache cache = new Cache();

    @Getter
    @Setter
    public static class Reservation {
        /**
         * 좌석 선점 유지 시간 (분)
         */
        private long ttlMinutes = 5;

        /**
         * 사용자당 최대 선점 가능 좌석 수
         */
        private int maxSeatCount = 4;

        /**
         * 마지막 업데이트 시간 캐시 TTL (시간)
         */
        private long lastUpdateTtlHours = 1;
    }

    @Getter
    @Setter
    public static class Polling {
        /**
         * 기본 폴링 타임아웃 (ms)
         */
        private long defaultTimeoutMs = 30000;

        /**
         * 최대 폴링 타임아웃 (ms)
         */
        private long maxTimeoutMs = 60000;

        /**
         * 최소 폴링 타임아웃 (ms)
         */
        private long minTimeoutMs = 5000;
    }

    @Getter
    @Setter
    public static class Session {
        /**
         * 콘서트당 최대 세션 수
         */
        private int maxSessionsPerConcert = 1000;

        /**
         * 세션 정리 간격 (분)
         */
        private long cleanupMinutes = 5;
    }

    @Getter
    @Setter
    public static class Lock {
        /**
         * 락 획득 대기 시간 (초)
         */
        private long waitTimeSeconds = 3;

        /**
         * 락 보유 시간 (초)
         */
        private long leaseTimeSeconds = 10;
    }

    @Getter
    @Setter
    public static class Cache {
        /**
         * 캐시 워밍업 시작 시간 (분 전)
         */
        private long warmupMinutesBefore = 10;
    }
}