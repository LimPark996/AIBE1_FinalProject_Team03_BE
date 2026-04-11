package com.team03.ticketmon.auth.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Redis에 저장되는 Refresh Token 도큐먼트 모델. id 필드는 userId를 의미한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    private Long id;

    private String token;

    private LocalDateTime created_at;
}
