package com.team03.ticketmon._global.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 캐시별 선택적 직렬화 설정
 *
 * [ 쉬운 설명 ]
 * Redis에 데이터를 저장할 때 "어떤 형식으로 저장할지"를 캐시마다 다르게 설정하는 클래스
 *
 * 왜 2가지 방식이 필요한가?
 * → 기존 좌석 캐시: 이미 타입 정보 없이 저장되어 있음 → 방식을 바꾸면 기존 데이터와 충돌
 * → 새로운 콘서트 캐시: 타입 정보를 포함해야 ClassCastException(형변환 에러)이 안 남
 *
 * ClassCastException이란?
 * → JSON을 다시 자바 객체로 변환할 때, "이게 어떤 클래스인지" 모르면 발생하는 에러
 * → 타입 정보를 같이 저장하면 해결됨
 */

@Configuration

@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public ObjectMapper legacyObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    public ObjectMapper typedObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();

        objectMapper.activateDefaultTyping(
                typeValidator,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return objectMapper;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        GenericJackson2JsonRedisSerializer legacySerializer =
                new GenericJackson2JsonRedisSerializer(legacyObjectMapper());

        GenericJackson2JsonRedisSerializer typedSerializer =
                new GenericJackson2JsonRedisSerializer(typedObjectMapper());

        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(legacySerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put("seatInfo", defaultCacheConfig
                .entryTtl(Duration.ofHours(1)));

        cacheConfigurations.put("seatExists", defaultCacheConfig
                .entryTtl(Duration.ofHours(2)));

        cacheConfigurations.put("venueInfo", defaultCacheConfig
                .entryTtl(Duration.ofHours(12)));

        cacheConfigurations.put("concertQueueStatus", defaultCacheConfig
                .entryTtl(Duration.ofMinutes(5)));

        RedisCacheConfiguration typedCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(typedSerializer))  // 타입 정보 포함 직렬화
                .disableCachingNullValues();

        cacheConfigurations.put("concertDetail", typedCacheConfig
                .entryTtl(Duration.ofMinutes(15)));

        cacheConfigurations.put("searchResults", typedCacheConfig
                .entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}