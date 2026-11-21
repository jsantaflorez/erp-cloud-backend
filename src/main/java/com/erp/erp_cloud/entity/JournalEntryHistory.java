package com.erp.erp_cloud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Bloque A (2026-11-21, F1-02 / T-01): one saved version of a journal entry.
 *
 * Written by JournalEntryHistoryService right BEFORE an entry is edited or
 * annulled, with the full entry (header + every line) as it was, as JSON.
 * Append-only: nothing in the application updates or deletes these rows.
 *
 * Plain ids instead of @ManyToOne on purpose: a history row must never be
 * loaded, cascaded or changed together with the live entry.
 */
@Entity
@Table(name = "journal_entry_history")
@Getter
@Setter
public class JournalEntryHistory {

    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_ANNUL = "ANNUL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "journal_entry_id", nullable = false)
    private Long journalEntryId;

    /** @Version of the entry this snapshot shows (the one being replaced). */
    @Column(name = "entry_version", nullable = false)
    private Long entryVersion;

    /** What was done to the entry right after this snapshot: UPDATE or ANNUL. */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    /** JournalEntryResponseDTO of the entry before the change, as JSON. */
    @Column(name = "snapshot", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshot;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    /** Username (email) of who made the change; "system" without a user. */
    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
