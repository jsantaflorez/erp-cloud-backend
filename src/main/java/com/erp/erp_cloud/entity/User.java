package com.erp.erp_cloud.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;


@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email")
})
@Audited
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    @NotAudited
    private String passwordHash;

    private String firstName;
    private String lastName;
    private boolean active = true;
    private boolean emailVerified = false;

    // Security Control fields
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    private Integer failedLoginAttempts = 0;
    private LocalDateTime lockedUntil;

    // MFA fields (Fase 2)
    private boolean mfaEnabled = false;
    private String mfaSecret;

    public String getFullName() {
        return String.format("%s %s", firstName, lastName);
    }
}