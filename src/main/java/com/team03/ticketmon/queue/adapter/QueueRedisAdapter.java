package com.team03.ticketmon.queue.adapter;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * QueueRedisAdapter — 대기열 도메인 전용 Redis 접근 어댑터
 *
 * 이 클래스가 하는 일:
 *   1. 대기열(Sorted Set), 활성 사용자 카운터, 활성 세션, AccessKey 등
 *      Redis 자료구조 객체를 반환 (RBucket/RScoredSortedSet/RAtomicLong/RLock/RTopic)
 *   2. 타임스탬프 + 원자적 시퀀스를 결합한 유니크한 대기열 점수(score) 생성
 *   3. 스케줄러들의 분산 락 및 Pub/Sub 토픽 획득 창구 제공
 *
 * 동작 흐름:
 *   - 서비스 계층은 Redis 키 포맷이나 Codec 을 직접 알 필요 없이
 *     이 어댑터를 통해 필요한 Redisson 객체를 받아 사용
 *   - generateQueueScore 는 동일 ms 내 요청 충돌을 막기 위해
 *     (timestamp << 21) | sequence 형태의 유일 점수를 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueRedisAdapter {
    private final RedissonClient redissonClient;
    private final RedisKeyGenerator keyGenerator;

    private static final String SEQUENCE_KEY_SUFFIX = ":seq:";
    private static final int SEQUENCE_BITS = 21;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    /**
     * 타임스탬프와 원자적 시퀀스를 조합하여 유니크한 대기열 점수(score)를 생성
     * 대기열 점수 생성에 대한 모든 책임
     * @param concertId 점수를 생성할 콘서트 ID
     * @return 생성된 유니크한 점수(long)
     */
    public long generateQueueScore(Long concertId) {
        long timestamp = System.currentTimeMillis();
        String queueKey = keyGenerator.getWaitQueueKey(concertId);
        String sequenceKey = queueKey + SEQUENCE_KEY_SUFFIX + timestamp;

        RAtomicLong sequence = redissonClient.getAtomicLong(sequenceKey);
        long currentSequence = sequence.incrementAndGet();

        if (sequence.remainTimeToLive() == -1) {
            sequence.expire(Duration.ofMillis(200));
        }

        if (currentSequence > MAX_SEQUENCE) {
            log.error("1ms 내 요청 한도 초과! ({}개 이상)", MAX_SEQUENCE);
            throw new BusinessException(ErrorCode.QUEUE_TOO_MANY_REQUESTS);
        }

        return (timestamp << SEQUENCE_BITS) | currentSequence;
    }

    /**
     * 특정 콘서트의 대기열(Sorted Set) 객체를 반환
     * 외부 서비스(스케줄러 등)에서 대기열의 상태를 조회할 때 사용
     *
     * @param concertId 조회할 콘서트의 ID
     * @return 해당 콘서트의 대기열 RScoredSortedSet 객체
     */
    public RScoredSortedSet<Long> getQueue(Long concertId) {
        String queueKey = keyGenerator.getWaitQueueKey(concertId);
        return redissonClient.getScoredSortedSet(queueKey, LongCodec.INSTANCE);
    }

    /**
     * 특정 콘서트의 활성 사용자 수(AtomicLong) 객체를 반환
     */
    public RAtomicLong getActiveUserCounter(Long concertId) {
        String countKey = keyGenerator.getActiveUsersCountKey(concertId);
        return redissonClient.getAtomicLong(countKey);
    }

    /**
     * 특정 콘서트의 활성 세션(Sorted Set) 객체를 반환
     */
    public RScoredSortedSet<Long> getActiveSessions(Long concertId) {
        String sessionsKey = keyGenerator.getActiveSessionsKey(concertId);
        return redissonClient.getScoredSortedSet(sessionsKey, LongCodec.INSTANCE);
    }

    /**
     * 사용자의 특정 콘서트의 accessKey(Bucket) 객체를 반환
     */
    public RBucket<String> getAccessKeyBucket(Long concertId, Long userId) {
        String accessKey = keyGenerator.getAccessKey(concertId, userId);
        return redissonClient.getBucket(accessKey);
    }

    /**
     * 사용자의 특정 콘서트의 최종 만료 시각(Bucket) 객체를 반환
     * @param concertId 조회할 콘서트 ID
     * @param userId 조회할 사용자 ID
     * @return 최종 만료 시각(Long)을 담는 RBucket 객체
     */
    public RBucket<Long> getFinalExpiryBucket(Long concertId, Long userId) {
        String finalExpiryKey = keyGenerator.getFinalExpiryKey(concertId, userId);
        return redissonClient.getBucket(finalExpiryKey);
    }

    /**
     * 입장 처리 스케줄러용 분산 락 반환 (다중 인스턴스 동시 실행 방지).
     */
    public RLock getAdmissionSchedulerLock() {
        String key = RedisKeyGenerator.ADMISSION_SCHEDULER_LOCK_KEY;
        return redissonClient.getLock(key);
    }

    public RLock getCleanupSchedulerLock() {
        String key = RedisKeyGenerator.CLEANUP_SCHEDULER_LOCK_KEY;
        return redissonClient.getLock(key);
    }

    public RLock getConsistencyCheckLock() {
        String key = RedisKeyGenerator.CONSISTENCY_CHECK_LOCK_KEY;
        return redissonClient.getLock(key);
    }

    public RTopic getAdmissionTopic() {
        return redissonClient.getTopic(RedisKeyGenerator.ADMISSION_TOPIC);
    }

    public RTopic getRankUpdateTopic() {
        return redissonClient.getTopic(RedisKeyGenerator.RANK_UPDATE_TOPIC);
    }
}