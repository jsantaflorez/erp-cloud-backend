package com.erp.erp_cloud.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journal_entries")
@Getter
@Setter
public class JournalEntry {

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