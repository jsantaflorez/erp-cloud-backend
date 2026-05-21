package com.erp.erp_cloud.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;


@Entity
@Table(name = "permissions")
@Immutable
@Getter @Setter
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String resource;  // e.g., "JOURNAL_ENTRY"
    private String action;    // e.g., "ANNUL"

    @Column(unique = true, nullable = false)
    private String code;      // e.g., "JOURNAL_ENTRY_ANNUL"

    private String module;    // e.g., "ACCOUNTING"
}