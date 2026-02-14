package com.team03.ticketmon._global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 설정 클래스
 *
 * [ 쉬운 설명 ]
 * 이 클래스를 켜면 다른 클래스에서 @Scheduled 어노테이션을 사용할 수 있음
 *
 * @Scheduled 사용 예시:
 *   @Scheduled(fixedRate = 5 * 60 * 1000)  ← 5분마다 실행
 *   public void warmUpSeatCache() {
 *       // 좌석 캐시를 미리 준비하는 코드
 *   }
 *
 *   @Scheduled(fixedRate = 10 * 1000)       ← 10초마다 실행
 *   public void processWaitingQueue() {
 *       // 대기열을 처리하는 코드
 *   }
 */

@Configuration
@EnableScheduling
public class SchedulerConfig {

}