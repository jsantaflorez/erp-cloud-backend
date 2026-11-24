package com.erp.erp_cloud.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "journal_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_doctype_consecutive_active",
                        columnNames = {"company_id", "document_type_id", "consecutive", "is_active"}
                ),
                // V6 (2026-11-12): unique PER COMPANY. It used to be a global
                // @Column(unique = true), so two companies with the same
                // prefix collided on "FV-1".
                @UniqueConstraint(
                        name = "uk_journal_entries_company_docnum",
                        columnNames = {"company_id", "document_number"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_journal_entries_annulled_status",
                        columnList = "company_id, is_annulled"
                ),
                @Index(
                        name = "idx_journal_entries_date_lookup",
                        columnList = "company_id, entry_date"
                )
        }
)
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class  JournalEntry extends BaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate entryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentType documentType;

    @Column(nullable = false)
    private Long consecutive; // The number assigned from DocumentType.current_consecutive

    @Column(nullable = false, length = 20)
    private String documentNumber;

    @Column(length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_annulled", nullable = false)
    private boolean annulled = false;

    @Column(name = "annulled_at")
    private java.time.LocalDateTime annulledAt;

    @Column(name = "annulled_by")
    private String annulledBy;

    @Column(name = "annulment_reason")
    private String annulmentReason;

    // Bloque A (2026-11-21, F1-20): optimistic lock. Hibernate increments it
    // on every update; JournalEntryService.update() also compares it with
    // the version the client loaded, so a second user saving an older copy
    // gets ENTRY_MODIFIED_BY_OTHER_USER instead of overwriting the first.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // Bloque A (2026-11-21, F1-03): client-generated key (Idempotency-Key
    // header). Unique per company (V7), so the same create request arriving
    // twice returns the entry already created instead of a second one.
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    // Bloque A fase 3 (2026-11-24, V9): annulment by reverse. On the
    // ORIGINAL, reversalEntry is the ANULACION entry that cancels it; on the
    // REVERSE, reversalOf is the original. Excluded from equals/hashCode:
    // the two point at each other and would recurse forever.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_entry_id")
    @EqualsAndHashCode.Exclude
    private JournalEntry reversalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id", unique = true)
    @EqualsAndHashCode.Exclude
    private JournalEntry reversalOf;

    /** True for a system-generated ANULACION entry (it reverses another one). */
    public boolean isReversal() {
        return reversalOf != null;
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // This is the other side of the relationship
    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JournalEntryItem> items = new ArrayList<>();

    /**
     * Helper method to maintain the bi-directional relationship
     */
    public void addItem(JournalEntryItem item) {
        items.add(item);
        item.setJournalEntry(this);
    }
}