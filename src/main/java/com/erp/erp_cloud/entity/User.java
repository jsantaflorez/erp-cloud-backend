package com.erp.erp_cloud.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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


    @NotAudited
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<UserRole> userRoles = new HashSet<>();

    public String getFullName() {
        return Stream.of(firstName, lastName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }



}