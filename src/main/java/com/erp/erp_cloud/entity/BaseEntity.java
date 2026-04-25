package com.erp.erp_cloud.entity;


import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base abstract class to provide auditing fields for all entities.
 * Uses JPA Auditing to automatically manage timestamps.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // TODO: Link these fields to Spring Security context after JWT implementation
    /**
     * Stores the username who created the record.
     * Controlled by Spring Data Auditing via @CreatedBy.
     */
    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 50)
    private String createdBy;

    /**
     * Stores the username who last modified the record.
     * Controlled by Spring Data Auditing via @LastModifiedBy.
     */
    @LastModifiedBy
    @Column(name = "updated_by", length = 50)
    private String updatedBy;



}