package com.erp.erp_cloud.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "journal_entry_items")
@Getter
@Setter
public class JournalEntryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccounts account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "third_party_id")
    private ThirdParty thirdParty;

    /**
     * Stores the specific Cost Center or Sub-center.
     * Business logic will ensure this center has allowsMovement = true.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    /**
     * Validates the item before persisting to the database.
     */
    @PrePersist
    @PreUpdate
    private void validateAmounts() {
        boolean hasDebit = debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit && hasCredit) {
            throw new IllegalStateException("An item cannot have both a debit and a credit.");
        }
        if (!hasDebit && !hasCredit) {
            throw new IllegalStateException("An item must have either a debit or a credit.");
        }
    }

    @Column(length = 255)
    private String description;
}