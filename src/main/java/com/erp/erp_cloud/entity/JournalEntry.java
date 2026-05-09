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

    @Column(nullable = false, length = 20, unique = true)
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