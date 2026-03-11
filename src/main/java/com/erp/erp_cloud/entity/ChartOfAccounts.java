package com.erp.erp_cloud.entity;


import com.erp.erp_cloud.enums.AccountNature;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;



import java.util.ArrayList;
import java.util.List;
@Entity
@Table(
        name = "chart_of_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_company_code", columnNames = {"company_id", "code"})
        },
        indexes = {
                // For searching by code (most common query)
                @Index(name = "idx_coa_company_code", columnList = "company_id, code"),

                // For hierarchy queries (finding children)
                @Index(name = "idx_coa_parent", columnList = "company_id, parent_id"),

                // For filtering posting accounts (journal entry selector)
                @Index(name = "idx_coa_posting", columnList = "company_id, posting_account, active"),

                // For level-based queries
                @Index(name = "idx_coa_level", columnList = "company_id, level, active"),

                // For search by name
                @Index(name = "idx_coa_name", columnList = "company_id, name"),

                // For filtering by account class
                @Index(name = "idx_coa_class", columnList = "company_id, account_class")
        }
)

@Data
public class ChartOfAccounts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore  // Prevent infinite recursion
    private Company company;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private Byte level;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private AccountNature nature;

    @Column(nullable = false, length = 20)
    private String accountClass;

    @Column(length = 20)
    private String accountType;

    @Column(nullable = false)
    private boolean postingAccount = false;

    @Column(nullable = false)
    private boolean requiresThirdParty = false;

    @Column(nullable = false)
    private boolean requiresCostCenter = false;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties({"parent", "children", "company"})  // Prevent circular references
    private ChartOfAccounts parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @JsonIgnore  // Don't serialize children to avoid deep object graphs
    private List<ChartOfAccounts> children = new ArrayList<>();
}