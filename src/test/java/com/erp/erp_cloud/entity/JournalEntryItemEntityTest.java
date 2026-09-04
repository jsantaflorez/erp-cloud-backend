package com.erp.erp_cloud.entity;

import com.erp.erp_cloud.exception.InvalidOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests for {@link JournalEntryItem#validateAmounts()} -- no
 * Spring context, no database. Only ever runs as a JPA
 * @PrePersist/@PreUpdate lifecycle callback, so nothing in
 * JournalEntryServiceTest (repository.save() is mocked) ever triggered it,
 * leaving it with zero coverage before this file -- exactly the same class
 * of gap as ChartOfAccounts.validateEntity() found the day before.
 *
 * REGRESSION CONTEXT: JournalEntryService.annul() deliberately zeroes out
 * every item's debit AND credit to neutralize an entry's financial effect.
 * Before the fix under test here, this callback rejected that exact state
 * on every single annulment ("An item must have either a debit or a
 * credit."), surfaced to the user as a raw, untranslated 500 error --
 * meaning annul() could never actually succeed against a real database.
 */
class JournalEntryItemEntityTest {

    private JournalEntryItem item(BigDecimal debit, BigDecimal credit) {
        JournalEntryItem item = new JournalEntryItem();
        item.setDebit(debit);
        item.setCredit(credit);
        return item;
    }

    @Test
    @DisplayName("validateAmounts() accepts a debit-only line")
    void validateAmounts_debitOnly_succeeds() {
        assertThatCode(() -> item(new BigDecimal("100.00"), BigDecimal.ZERO).validateAmounts())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateAmounts() accepts a credit-only line")
    void validateAmounts_creditOnly_succeeds() {
        assertThatCode(() -> item(BigDecimal.ZERO, new BigDecimal("100.00")).validateAmounts())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateAmounts() rejects a line with both debit and credit")
    void validateAmounts_bothAmounts_throwsWithStableCode() {
        JournalEntryItem item = item(new BigDecimal("50.00"), new BigDecimal("50.00"));

        assertThatThrownBy(item::validateAmounts)
                .isInstanceOfSatisfying(InvalidOperationException.class, ex -> {
                    assertThat(ex.getMessage()).contains("cannot have both");
                    assertThat(ex.getErrorCode()).isEqualTo("ITEM_HAS_BOTH_DEBIT_AND_CREDIT");
                });
    }

    @Test
    @DisplayName("validateAmounts() rejects a line with neither debit nor credit, on a non-annulled entry")
    void validateAmounts_neitherAmount_notAnnulled_throwsWithStableCode() {
        JournalEntry entry = new JournalEntry();
        entry.setAnnulled(false);
        JournalEntryItem item = item(BigDecimal.ZERO, BigDecimal.ZERO);
        entry.addItem(item);

        assertThatThrownBy(item::validateAmounts)
                .isInstanceOfSatisfying(InvalidOperationException.class, ex -> {
                    assertThat(ex.getMessage()).contains("must have either");
                    assertThat(ex.getErrorCode()).isEqualTo("ITEM_MISSING_DEBIT_OR_CREDIT");
                });
    }

    @Test
    @DisplayName("validateAmounts() rejects a zeroed line with no parent entry set at all")
    void validateAmounts_neitherAmount_noParentEntry_throws() {
        // Defense in depth: a detached item (journalEntry == null) must
        // still be rejected rather than silently skipped.
        assertThatThrownBy(() -> item(BigDecimal.ZERO, BigDecimal.ZERO).validateAmounts())
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("validateAmounts() accepts a zeroed line when its parent entry is annulled (REGRESSION GUARD)")
    void validateAmounts_neitherAmount_annulledEntry_succeeds() {
        JournalEntry entry = new JournalEntry();
        entry.setAnnulled(true);
        JournalEntryItem item = item(BigDecimal.ZERO, BigDecimal.ZERO);
        entry.addItem(item);

        assertThatCode(item::validateAmounts).doesNotThrowAnyException();
    }
}
