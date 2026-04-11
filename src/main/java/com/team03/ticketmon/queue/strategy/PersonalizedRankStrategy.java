package com.team03.ticketmon.queue.strategy;

import com.team03.ticketmon.queue.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Collection;

/**
 * PersonalizedRankStrategy — 대기열 상위 N명 개인화 순위 알림 전략
 *
 * 이 클래스가 하는 일:
 *   1. 대기열 Sorted Set 에서 상위 top-ranker-count 명 조회
 *   2. 각 사용자에게 1:1 로 현재 순위 이벤트를 Pub/Sub 발행
 *
 * 동작 흐름:
 *   - WaitingQueueScheduler 가 입장 처리 후 호출
 *   - NotificationStrategy 인터페이스 구현체로 전략 패턴 적용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalizedRankStrategy implements NotificationStrategy {

    private final NotificationService notificationService;

    @Value("${app.queue.top-ranker-count}")
    private int topRankerCount;

    @Override
    public void execute(Long concertId, RScoredSortedSet<Long> queue) {
        if (queue == null || queue.isEmpty() || topRankerCount <= 0) {
            return;
        }

        // 1. 대기열의 최상위 N명의 ID와 점수를 조회합니다.
        Collection<ScoredEntry<Long>> topRankers = queue.entryRange(0, topRankerCount - 1);
        if (topRankers == null || topRankers.isEmpty()) {
            return;
        }

        log.debug("[Notification] 콘서트 ID {}: 최상위 {}명에게 개인 순위 알림 전송 시작.", concertId, topRankers.size());

        // 2. 각 사용자에게 개인화된 순위 정보를 1:1 메시지로 전송합니다.
        int rank = 1;
        for (ScoredEntry<Long> entry : topRankers) {
            Long userId = entry.getValue();

            try {
                // NotificationService에 개별 순위 전송을 위한 새 메서드 호출
                notificationService.sendRankUpdate(userId, rank);
            } catch (Exception e) {
                log.error("[Notification] 사용자 {}에게 순위 알림 전송 실패: {}", userId, e.getMessage());
            }
            rank++;
        }
    }
}