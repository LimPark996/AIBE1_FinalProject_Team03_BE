package com.team03.ticketmon._global.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.redisson.codec.JsonJacksonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redisson 설정 클래스
 *
 * [ 쉬운 설명 ]
 * 이 프로젝트에는 Redis 관련 설정이 3개 있음:
 *   1) CacheConfig: 캐시(@Cacheable) 전용 설정
 *   2) RedisConfig: Redis 기본 작업(get/set) 도구 설정 (RedisTemplate)
 *   3) RedissonConfig: Redis 고급 기능(분산 락, Pub/Sub) 도구 설정 (이 클래스!)
 *
 * 왜 RedisTemplate과 Redisson을 둘 다 쓸까?
 * → RedisTemplate: 단순한 데이터 저장/조회에 적합
 * → Redisson: 분산 락처럼 복잡한 기능이 필요할 때 적합
 * → 각자 잘하는 게 달라서 용도에 따라 나눠 사용
 */

@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.username:#{null}}")
    private String redisUsername;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        ObjectMapper om = new ObjectMapper()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .registerModule(new JavaTimeModule());

        config.setCodec(new JsonJacksonCodec(om));

        String protocol = sslEnabled ? "rediss" : "redis";
        String redisUrl = "%s://%s:%d".formatted(protocol, redisHost, redisPort);

        log.debug("Redisson Client를 생성합니다. Address: {}", redisUrl);

        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(redisUrl)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(10)
                .setRetryAttempts(3)
                .setRetryInterval(1000)
                .setTimeout(3000);

        if (StringUtils.hasText(redisUsername)) {
            serverConfig.setUsername(redisUsername);
        }
        if (StringUtils.hasText(redisPassword)) {
            serverConfig.setPassword(redisPassword);
        }

        return Redisson.create(config);
    }
}