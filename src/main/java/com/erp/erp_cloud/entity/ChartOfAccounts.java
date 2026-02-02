package com.erp.erp_cloud.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(
        name = "chart_of_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_code",
                        columnNames = {"company_id", "code"}
                )
        }
)
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // Avoid proxy errors
public class ChartOfAccounts implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Company that owns the chart of accounts
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;


    @Column(name = "code", nullable = false, length = 20)
    private String code;

    /**
     * account name
     */
    @Column(name = "name", nullable = false, length = 150)
    private String name;


    @Column(nullable = false)
    private Byte level;


    @Column(nullable = false, length = 1)
    private String nature; //D or C (debit or credit)


    @Column(nullable = false, length = 20)
    private String accountClass;




    @Column(length = 20)
    private String accountType; // * Corriente, No Corriente, Resultados, etc.


    @Column(nullable = false)
    private boolean postingAccount = false;// true = allows movements



    @Column(nullable = false)
    private boolean requiresThirdParty = false;

    @Column(nullable = false)
    private boolean requiresCostCenter = false;


    @Column(nullable = false)
    private boolean active = true;

    // =========================
    // hierarchy
    // =========================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties("children") // Prevent the parent from loading its children to avoid infinite recursion
    private ChartOfAccounts parent;

    @OneToMany(
            mappedBy = "parent",
            fetch = FetchType.LAZY
    )

    @JsonIgnore // For now, we're ignoring children to make the GET request fast and non-recursive.
    private List<ChartOfAccounts> children = new ArrayList<>();

}
