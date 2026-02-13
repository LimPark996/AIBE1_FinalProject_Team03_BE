package com.team03.ticketmon._global.config;

// Jackson 라이브러리: 자바 객체 ↔ JSON 변환을 담당하는 도구 모음
// 자바 객체를 Redis에 저장하려면 JSON 문자열로 변환해야 함 (이것을 "직렬화"라고 부름)
// @JsonTypeInfo: JSON으로 변환할 때 "이 데이터가 어떤 클래스(타입)인지" 정보를 함께 저장하는 설정
// → 예: {"@class": "Concert", "name": "콘서트A"} 처럼 클래스 이름도 같이 저장
// → 왜 필요? 나중에 JSON을 다시 자바 객체로 변환할 때 "어떤 클래스로 변환해야 하는지" 알 수 있음
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// ObjectMapper: JSON ↔ 자바 객체 변환을 실제로 수행하는 핵심 도구
// → 자바 세계의 "번역기" 같은 존재
// → 예: Concert 객체 → {"name":"콘서트A"} (직렬화)
// → 예: {"name":"콘서트A"} → Concert 객체 (역직렬화)
import com.fasterxml.jackson.databind.ObjectMapper;

// SerializationFeature: ObjectMapper의 직렬화 옵션을 설정하는 열거형(enum)
// → 예: 날짜를 "2025-01-15" 형식으로 저장할지, 1705276800000 같은 숫자로 저장할지 결정
import com.fasterxml.jackson.databind.SerializationFeature;

// BasicPolymorphicTypeValidator: 타입 정보를 포함할 때 "어떤 클래스까지 허용할지" 검증하는 도구
// → 보안 목적: 아무 클래스나 허용하면 악의적인 객체가 역직렬화될 수 있음
// → "다형성(Polymorphic)": 하나의 변수가 여러 타입의 객체를 담을 수 있는 것
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

// JavaTimeModule: Java 8의 날짜/시간 클래스(LocalDateTime 등)를 JSON으로 변환할 수 있게 해주는 모듈
// → 이걸 등록 안 하면 LocalDateTime 같은 타입을 JSON으로 변환할 때 에러 발생
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// CacheManager: 스프링의 캐시(임시 저장소)를 관리하는 인터페이스
// → "캐시"란? 자주 사용하는 데이터를 빠른 저장소에 미리 저장해두는 것
// → 예: 매번 DB에서 콘서트 정보를 조회하면 느리니까, Redis에 캐시해두고 빠르게 가져옴
import org.springframework.cache.CacheManager;

// @EnableCaching: 스프링의 캐시 기능을 "켜는" 스위치
// → 이걸 붙여야 @Cacheable, @CacheEvict 같은 캐시 어노테이션이 동작함
import org.springframework.cache.annotation.EnableCaching;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// @Primary: 같은 타입의 빈이 여러 개 있을 때, "기본값"으로 사용할 빈을 지정
// → 예: ObjectMapper가 2개 있으면, 스프링이 어떤 걸 쓸지 혼란 → @Primary가 붙은 걸 우선 사용
import org.springframework.context.annotation.Primary;

// RedisCacheConfiguration: Redis 캐시의 세부 설정(TTL, 직렬화 방식 등)을 정의하는 클래스
// → TTL(Time To Live): 캐시가 얼마 동안 유효한지 (유통기한 같은 것)
import org.springframework.data.redis.cache.RedisCacheConfiguration;

// RedisCacheManager: Redis를 캐시 저장소로 사용하는 CacheManager 구현체
// → "Redis를 사용해서 캐시를 관리하겠다"는 뜻
import org.springframework.data.redis.cache.RedisCacheManager;

// RedisConnectionFactory: Redis 서버에 접속하기 위한 연결 공장
// → S3Client가 S3에 접속하는 도구였듯이, 이건 Redis에 접속하는 도구
import org.springframework.data.redis.connection.RedisConnectionFactory;

// GenericJackson2JsonRedisSerializer: 자바 객체를 JSON으로 변환해서 Redis에 저장하는 직렬화 도구
// → ObjectMapper를 내부적으로 사용해서 변환 수행
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

// RedisSerializationContext: Redis 직렬화 설정을 묶어주는 컨텍스트(맥락) 클래스
import org.springframework.data.redis.serializer.RedisSerializationContext;

// StringRedisSerializer: 문자열을 그대로 Redis에 저장하는 직렬화 도구
// → 캐시의 "키(key)"는 보통 문자열이므로, 키 직렬화에 사용
import org.springframework.data.redis.serializer.StringRedisSerializer;

// Duration: 시간 간격을 표현하는 클래스 (예: 30분, 1시간, 12시간)
// → 캐시의 TTL(유효기간)을 설정할 때 사용
import java.time.Duration;

// HashMap: 키-값 쌍을 저장하는 자료구조 (사전/딕셔너리 같은 것)
// → 예: { "seatInfo" → 설정A, "concertDetail" → 설정B } 이런 식으로 캐시별 설정을 저장
import java.util.HashMap;

// Map: HashMap의 부모 인터페이스 (변수 타입으로 사용)
import java.util.Map;

/**
 * ✅ 캐시별 선택적 직렬화 설정
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

// ──────────────────────────────────────────────────────────────
// @Configuration: 설정 클래스임을 스프링에게 알려줌
// ──────────────────────────────────────────────────────────────
@Configuration

// ──────────────────────────────────────────────────────────────
// @EnableCaching: 스프링의 캐시 기능을 활성화하는 스위치
// → 이걸 켜야 다른 클래스에서 @Cacheable("concertDetail") 같은 캐시 어노테이션이 동작함
// → @Cacheable: "이 메서드의 결과를 캐시에 저장하고, 다음에 같은 요청이 오면 캐시에서 꺼내줘"
// ──────────────────────────────────────────────────────────────
@EnableCaching
public class CacheConfig {

    /**
     * 기존 방식 ObjectMapper (타입 정보 없음)
     * → 좌석 관련 캐시에 사용됨
     * → 이미 Redis에 타입 정보 없이 저장된 데이터가 있으므로, 호환성을 위해 유지
     */

    // ──────────────────────────────────────────────────────────
    // @Bean: 이 메서드가 반환하는 ObjectMapper를 스프링이 관리
    //
    // @Primary: ObjectMapper 빈이 2개(legacy, typed)이므로,
    //   다른 클래스에서 ObjectMapper를 주입받을 때 "기본적으로 이걸 써라"고 지정
    //   → 특별히 지정하지 않으면 이 legacyObjectMapper가 사용됨
    // ──────────────────────────────────────────────────────────
    @Bean
    @Primary
    public ObjectMapper legacyObjectMapper() {
        // ──────────────────────────────────────────────────────
        // new ObjectMapper(): JSON 변환기(번역기)를 새로 생성
        // ──────────────────────────────────────────────────────
        ObjectMapper objectMapper = new ObjectMapper();

        // ──────────────────────────────────────────────────────
        // JavaTimeModule 등록: LocalDateTime, LocalDate 같은 Java 8 날짜 타입을
        // JSON으로 변환할 수 있게 해주는 모듈을 추가
        // → 이걸 안 하면 LocalDateTime을 JSON으로 바꿀 때 에러 발생
        // ──────────────────────────────────────────────────────
        objectMapper.registerModule(new JavaTimeModule());

        // ──────────────────────────────────────────────────────
        // 날짜를 숫자(타임스탬프) 대신 문자열로 저장하도록 설정
        // → OFF: "2025-01-15T10:30:00" (사람이 읽기 쉬움) ✅
        // → ON:  1705276800000 (사람이 읽기 어려움) ❌
        // ──────────────────────────────────────────────────────
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 타입 정보를 포함하지 않음 → 기존 좌석 캐시 데이터와 호환성 유지
        return objectMapper;
    }

    /**
     * 새로운 방식 ObjectMapper (타입 정보 포함)
     * → 콘서트 관련 캐시에 사용됨
     * → 타입 정보를 JSON에 포함시켜 ClassCastException을 방지
     */
    @Bean
    public ObjectMapper typedObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // → 위 3줄은 legacyObjectMapper와 동일 (기본 설정)

        // ──────────────────────────────────────────────────────
        // ✅ 여기서부터가 legacy와 다른 부분! 타입 정보를 포함하는 설정

        // 타입 검증기(TypeValidator) 생성
        // → "어떤 클래스의 타입 정보를 허용할지" 규칙을 정함
        // → allowIfSubType(Object.class): Object의 하위 타입이면 모두 허용
        //   (Object는 자바의 모든 클래스의 최상위 부모이므로, 사실상 "모든 클래스 허용")
        // → 보안상 범위를 좁히는 게 좋지만, 여기서는 편의를 위해 전체 허용
        // ──────────────────────────────────────────────────────
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();

        // ──────────────────────────────────────────────────────
        // 기본 타입 정보 포함 기능을 활성화
        //
        // activateDefaultTyping(검증기, 적용범위, 저장방식)
        //
        // typeValidator: 위에서 만든 타입 검증기 (어떤 클래스를 허용할지)
        //
        // ObjectMapper.DefaultTyping.NON_FINAL:
        //   → final이 아닌 클래스에 대해 타입 정보를 포함함
        //   → 대부분의 일반 클래스가 해당됨
        //
        // JsonTypeInfo.As.PROPERTY:
        //   → 타입 정보를 JSON의 속성(property)으로 저장
        //   → 예: {"@class": "com.team03.Concert", "name": "콘서트A"}
        //   → "@class"라는 속성에 클래스 이름이 함께 저장됨
        //   → 나중에 역직렬화할 때 이 정보를 보고 올바른 클래스로 변환
        // ──────────────────────────────────────────────────────
        objectMapper.activateDefaultTyping(
                typeValidator,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return objectMapper;
    }

    /**
     * Redis 기반 캐시 매니저 설정
     * → 어떤 캐시에 어떤 설정(TTL, 직렬화 방식)을 적용할지 정의
     */

    // ──────────────────────────────────────────────────────────
    // @Bean: CacheManager 객체를 스프링이 관리
    //
    // 파라미터 RedisConnectionFactory connectionFactory:
    // → Redis 서버에 접속하기 위한 연결 도구
    // → 스프링이 자동으로 만들어서 넣어줌 (application.yml의 Redis 설정 기반)
    // ──────────────────────────────────────────────────────────
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // ──────────────────────────────────────────────────────
        // ✅ 기존 방식 직렬화 도구 (좌석 캐시용)
        // → legacyObjectMapper(타입 정보 없음)를 사용해서 JSON으로 변환
        // → 자바 객체 → JSON 문자열로 변환해서 Redis에 저장
        // ──────────────────────────────────────────────────────
        GenericJackson2JsonRedisSerializer legacySerializer =
                new GenericJackson2JsonRedisSerializer(legacyObjectMapper());

        // ──────────────────────────────────────────────────────
        // ✅ 새로운 방식 직렬화 도구 (콘서트 캐시용)
        // → typedObjectMapper(타입 정보 포함)를 사용해서 JSON으로 변환
        // → {"@class": "Concert", ...} 형태로 저장됨
        // ──────────────────────────────────────────────────────
        GenericJackson2JsonRedisSerializer typedSerializer =
                new GenericJackson2JsonRedisSerializer(typedObjectMapper());

        // ──────────────────────────────────────────────────────
        // 기본 캐시 설정 (특별히 지정하지 않은 캐시는 이 설정을 따름)
        //
        // RedisCacheConfiguration.defaultCacheConfig(): 기본 설정 템플릿을 가져옴
        //
        // .entryTtl(Duration.ofMinutes(30)):
        //   → TTL(유효기간) = 30분
        //   → 캐시에 저장된 데이터는 30분 후 자동 삭제됨
        //   → 편의점 도시락의 유통기한 같은 것!
        //
        // .serializeKeysWith(...):
        //   → 캐시의 "키"를 어떤 방식으로 저장할지 설정
        //   → StringRedisSerializer: 키를 일반 문자열로 저장
        //   → 예: "seatInfo::concert123" 이런 식으로 Redis에 키가 저장됨
        //
        // .serializeValuesWith(...):
        //   → 캐시의 "값(데이터)"을 어떤 방식으로 저장할지 설정
        //   → legacySerializer: 타입 정보 없이 JSON으로 변환해서 저장
        //
        // .disableCachingNullValues():
        //   → null(빈 값)은 캐시에 저장하지 않음
        //   → DB에서 조회했는데 결과가 없으면 캐시에 안 넣겠다는 뜻
        // ──────────────────────────────────────────────────────
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(legacySerializer))
                .disableCachingNullValues();

        // ──────────────────────────────────────────────────────
        // 캐시별 개별 설정을 담을 Map(사전) 생성
        // → 캐시 이름(String) → 해당 캐시의 설정(RedisCacheConfiguration)
        // → 예: { "seatInfo" → 1시간 설정, "concertDetail" → 15분 설정 }
        // ──────────────────────────────────────────────────────
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // ===== 기존 좌석 관련 캐시 (타입 정보 없음, 기존 호환성 유지) =====

        // ──────────────────────────────────────────────────────
        // "seatInfo" 캐시: 좌석 정보를 저장
        // → 기본 설정(defaultCacheConfig)을 기반으로, TTL만 1시간으로 변경
        // → 좌석 정보는 자주 바뀌지 않으므로 1시간 동안 캐시 유지
        // ──────────────────────────────────────────────────────
        cacheConfigurations.put("seatInfo", defaultCacheConfig
                .entryTtl(Duration.ofHours(1)));

        // ──────────────────────────────────────────────────────
        // "seatExists" 캐시: 좌석 존재 여부(있다/없다)를 저장
        // → TTL: 2시간 (단순한 true/false 데이터라 더 오래 캐시 가능)
        // ──────────────────────────────────────────────────────
        cacheConfigurations.put("seatExists", defaultCacheConfig
                .entryTtl(Duration.ofHours(2)));

        // ──────────────────────────────────────────────────────
        // "venueInfo" 캐시: 공연장 정보를 저장
        // → TTL: 12시간 (공연장 정보는 거의 안 바뀌므로 오래 캐시)
        // ──────────────────────────────────────────────────────
        cacheConfigurations.put("venueInfo", defaultCacheConfig
                .entryTtl(Duration.ofHours(12)));

        // ──────────────────────────────────────────────────────
        // "concertQueueStatus" 캐시: 콘서트 대기열 상태를 저장
        // → TTL: 5분 (대기열은 실시간으로 변하므로 짧게 캐시)
        // ──────────────────────────────────────────────────────
        cacheConfigurations.put("concertQueueStatus", defaultCacheConfig
                .entryTtl(Duration.ofMinutes(5)));

        // ===== 새로운 콘서트 관련 캐시 (타입 정보 포함) =====

        // ──────────────────────────────────────────────────────
        // 콘서트 캐시용 설정을 새로 만듦
        // → 기본 설정과 거의 같지만, 직렬화 도구만 typedSerializer로 바꿈
        // → typedSerializer: 타입 정보(@class)를 JSON에 포함시키는 직렬화 도구
        // → 이렇게 해야 Redis에서 데이터를 꺼낼 때 올바른 자바 클래스로 변환됨
        // ──────────────────────────────────────────────────────
        RedisCacheConfiguration typedCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(typedSerializer))  // ✅ 타입 정보 포함 직렬화
                .disableCachingNullValues();

        // ──────────────────────────────────────────────────────
        // "concertDetail" 캐시: 콘서트 상세 정보를 저장
        // → TTL: 15분 (콘서트 정보는 비교적 자주 조회되지만, 가끔 바뀔 수 있음)
        // → typedCacheConfig 사용: 타입 정보 포함
        // ──────────────────────────────────────────────────────
        cacheConfigurations.put("concertDetail", typedCacheConfig
                .entryTtl(Duration.ofMinutes(15)));

        // ──────────────────────────────────────────────────────
        // "searchResults" 캐시: 검색 결과를 저장
        // → TTL: 10분 (검색 결과는 자주 바뀔 수 있으므로 짧게)
        // → typedCacheConfig 사용: 타입 정보 포함
        // ──────────────────────────────────────────────────────
        cacheConfigurations.put("searchResults", typedCacheConfig
                .entryTtl(Duration.ofMinutes(10)));

        // ──────────────────────────────────────────────────────
        // 최종적으로 RedisCacheManager를 빌더 패턴으로 조립해서 반환
        //
        // .builder(connectionFactory): Redis 연결 정보를 넣어줌
        // .cacheDefaults(defaultCacheConfig): 기본 캐시 설정 지정
        //   → 위에서 개별 설정하지 않은 캐시는 이 기본 설정을 따름
        // .withInitialCacheConfigurations(cacheConfigurations):
        //   → 캐시별 개별 설정을 Map으로 전달
        //   → { "seatInfo" → 1시간, "concertDetail" → 15분(타입포함), ... }
        // .build(): 조립 완료!
        // ──────────────────────────────────────────────────────
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}