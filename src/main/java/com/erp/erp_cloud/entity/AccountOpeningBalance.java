package com.erp.erp_cloud.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Opening balance snapshot per account x third party x cost center, for a
 * given fiscal year. NEW (2026-10-03), part of the year-end closing
 * feature (Cierre de Año): regenerated in full every time the PRIOR year
 * is closed (AccountingPeriodService.closeYear), from the real balance as
 * of December 31 of that prior year, for every Balance Sheet account
 * (ASSET/LIABILITY/EQUITY -- i.e. closesAtYearEnd = false) that has one.
 * Income Statement accounts (4/5/6) never get a row here: they always
 * start a new year at zero, which is exactly what the closing entry's
 * own zeroing lines guarantee.
 *
 * Deliberately NOT yet read by any report -- this table is populated
 * starting now so the data exists, but migrating the Auxiliar/Balance de
 * Comprobación/Centro de Costo/Tercero reports to read from here instead
 * of summing the full history live is a separate follow-up phase.
 *
 * thirdParty/costCenter are nullable (an account with neither carries
 * both null). NOTE: MySQL treats each NULL as a distinct value for
 * uniqueness purposes, so a DB-level unique constraint on
 * (company, year, account, thirdParty, costCenter) would NOT actually
 * stop two null/null rows for the same account from coexisting -- the
 * real uniqueness guarantee is the regeneration algorithm itself
 * (AccountingPeriodService always deletes a year's rows before
 * re-inserting a freshly aggregated set, grouped in Java by this exact
 * key), not a DB constraint. Only a lookup index is declared here.
 */
@Entity
@Table(
        name = "account_opening_balances",
        indexes = {
                @Index(
                        name = "idx_opening_balance_lookup",
                        columnList = "company_id, year, account_id"
                )
        }
)
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AccountOpeningBalance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(nullable = false)
    private Integer year;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccounts account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "third_party_id")
    private ThirdParty thirdParty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;
}
