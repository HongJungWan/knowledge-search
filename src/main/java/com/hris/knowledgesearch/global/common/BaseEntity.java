package com.hris.knowledgesearch.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 모든 엔티티의 공통 감사(audit) 필드를 정의하는 BaseEntity.
 * <p>
 * 생성일시·수정일시를 JPA Auditing 으로 자동 관리하고, 소프트 삭제용 deletedAt 을 제공한다.
 * 보안/인증 의존성(CustomAuditingEntityListener 등) 없이 self-contained 하게 구성했다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    /** 생성일시 (INSERT 시 자동 채움) */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 수정일시 (UPDATE 시 자동 갱신) */
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** 소프트 삭제 시각 (NULL 이면 활성) */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * 소프트 삭제 처리: deletedAt 를 현재 시각으로 설정한다.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /**
     * 삭제 여부 확인.
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

}
