package com.erp.erp_cloud.entity;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base abstract class to provide auditing fields for all entities.
 * Uses JPA Auditing to automatically manage timestamps.
 */
/**
 * Unified Base Entity for ERP Cloud.
 * Combines standard structure, Jackson optimization, and Spring Data Auditing.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

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
    @Column(name = "created_by", updatable = false)
    private long createdBy;// ID of the User who created the record

    /**
     * Stores the username who last modified the record.
     * Controlled by Spring Data Auditing via @LastModifiedBy.
     */
    @LastModifiedBy
    @Column(name = "updated_by", length = 50)
    private Long updatedBy; // ID of the User who last updated the record



}