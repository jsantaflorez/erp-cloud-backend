package com.erp.erp_cloud.entity;

import jakarta.persistence.*;
import lombok.Getter;

import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_roles", indexes = {
        @Index(name = "idx_user_company_role", columnList = "user_id, company_id")
})
@Audited
@Getter @Setter
public class UserRole extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Company company;

    private LocalDateTime assignedAt = LocalDateTime.now();
}