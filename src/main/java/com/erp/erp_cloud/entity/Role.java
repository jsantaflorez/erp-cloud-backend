package com.erp.erp_cloud.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name = "roles", uniqueConstraints = {

        @UniqueConstraint(name = "uk_role_name_company", columnNames = {"name", "company_id"})
})
@Audited
@Getter @Setter
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // TODO: SECURITY-PHASE-3: Create 't_companies_aud' table in MySQL production
// and switch targetAuditMode to audited to prevent data tampering untraceability.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Company company; // null for system-wide roles

    @Column(nullable = false)
    private String name;     // e.g., "Contador Senior"

    @Column(nullable = false)
    private String code;     // e.g., "ACCOUNTANT"

    @Column(name = "system_role")
    private boolean systemRole = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED) // Also bypasses permissions audit for now
    private Set<Permission> permissions = new HashSet<>();
}