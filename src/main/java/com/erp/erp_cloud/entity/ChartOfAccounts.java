package com.erp.erp_cloud.entity;


import jakarta.persistence.*;
import lombok.Data;



import java.util.ArrayList;
import java.util.List;
@Entity
@Table(
        name = "chart_of_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_company_code", columnNames = {"company_id", "code"})
        }
)
@Data
public class ChartOfAccounts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private Byte level;

    @Column(nullable = false, length = 1)
    private String nature; // Keep as String for now if you prefer, but Enum is better

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
    private ChartOfAccounts parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private List<ChartOfAccounts> children = new ArrayList<>();
}