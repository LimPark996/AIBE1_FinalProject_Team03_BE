package com.team03.ticketmon._global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스
 *
 * [ 쉬운 설명 ]
 * Redis란?
 * → 데이터를 메모리(RAM)에 저장하는 초고속 저장소
 * → 데이터베이스(MySQL 등)보다 훨씬 빠름 (메모리 vs 디스크)
 * → 캐시, 세션, 대기열, 실시간 데이터 등에 사용
 *
 * 이 클래스는 Redis를 사용하기 위한 2가지 도구를 만듦:
 * 1) RedisTemplate: Redis에 데이터를 읽고/쓰는 범용 도구
 * 2) RedisMessageListenerContainer: Redis 이벤트(키 만료 등)를 감지하는 도구
 *
 * CacheConfig와의 차이:
 *   CacheConfig: @Cacheable 같은 "캐시 어노테이션"을 위한 설정 (추상화된 캐시)
 *   RedisConfig: Redis를 직접 조작하기 위한 설정 (범용 도구)
 */

@Configuration
public class RedisConfig {

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

		template.setKeySerializer(new StringRedisSerializer());
		template.setHashKeySerializer(new StringRedisSerializer());

		template.setValueSerializer(jsonSerializer);
		template.setHashValueSerializer(jsonSerializer);

		return template;
	}

	/**
	 * Redis Message Listener Container 설정
	 * → Redis에서 발생하는 이벤트(예: 키 만료)를 감지하는 컨테이너
	 *
	 * [ 쉬운 설명 ]
	 * Redis에 "좌석 임시 예약" 데이터를 저장하고 5분 후 만료되게 설정했다고 가정
	 * → 5분 후 키가 만료됨 → Redis가 "키가 만료됐어!" 이벤트를 발생시킴
	 * → 이 컨테이너가 그 이벤트를 감지함
	 * → SeatExpirationEventListener가 "그러면 좌석 예약을 취소하자" 처리
	 *
	 * @param connectionFactory Redis 연결 팩토리
	 * @return Redis 메시지 리스너 컨테이너
	 */

	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		return container;
	}
}