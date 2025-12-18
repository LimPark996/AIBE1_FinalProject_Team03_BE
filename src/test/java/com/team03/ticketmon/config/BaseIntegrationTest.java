package com.team03.ticketmon.config;

import com.team03.ticketmon.queue.adapter.QueueRedisAdapter;
import com.team03.ticketmon.seat.service.SeatStatusEventSubscriber;
import com.team03.ticketmon.websocket.subscriber.RedisMessageSubscriber;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @MockitoBean
    protected RedissonClient redissonClient;

    @MockitoBean
    protected RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    protected ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @MockitoBean
    protected RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    protected StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    protected RedisMessageListenerContainer redisMessageListenerContainer;

    @MockitoBean
    protected QueueRedisAdapter queueRedisAdapter;

    @MockitoBean
    protected SeatStatusEventSubscriber seatStatusEventSubscriber;

    @MockitoBean
    protected RedisMessageSubscriber redisMessageSubscriber;
    @MockitoBean
    protected ClientRegistrationRepository clientRegistrationRepository;
}