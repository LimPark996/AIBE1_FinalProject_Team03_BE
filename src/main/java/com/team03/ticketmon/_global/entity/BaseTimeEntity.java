package com.team03.ticketmon._global.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * BaseTimeEntity — 생성/수정 시각을 자동 관리하는 공통 엔티티 베이스
 *
 * JPA Auditing 을 통해 엔티티가 저장/수정될 때 {@code createdAt}, {@code updatedAt}
 * 컬럼이 자동으로 기록됩니다. 이 클래스를 상속받는 엔티티는 별도 코드 없이 시간 추적이 가능합니다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseTimeEntity {

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}