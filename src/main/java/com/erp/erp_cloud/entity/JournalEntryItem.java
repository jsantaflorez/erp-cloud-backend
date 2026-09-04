package com.erp.erp_cloud.entity;

import com.erp.erp_cloud.exception.InvalidOperationException;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "journal_entry_items")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class JournalEntryItem extends BaseEntity implements Serializable {

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
     *
     * Package-private (not private) so a plain unit test can call it
     * directly -- this only ever ran via a real JPA lifecycle callback, so
     * it had zero test coverage before the bug below was found.
     */
    @PrePersist
    @PreUpdate
    void validateAmounts() {
        // BUG FIX: JournalEntryService.annul() deliberately zeroes out
        // every item's debit AND credit to neutralize the entry's
        // financial effect (see annul()) -- that's the one legitimate case
        // where neither amount is set. Without this exception, annul()
        // could never actually succeed: this callback rejected the exact
        // zeroed items it produces, on every single annulment, with a raw
        // English "must have either a debit or a credit" message.
        if (journalEntry != null && journalEntry.isAnnulled()) {
            return;
        }

        boolean hasDebit = debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit && hasCredit) {
            throw new InvalidOperationException(
                    "An item cannot have both a debit and a credit.", "ITEM_HAS_BOTH_DEBIT_AND_CREDIT");
        }
        if (!hasDebit && !hasCredit) {
            throw new InvalidOperationException(
                    "An item must have either a debit or a credit.", "ITEM_MISSING_DEBIT_OR_CREDIT");
        }
    }

    @Column(length = 255)
    private String description;
}