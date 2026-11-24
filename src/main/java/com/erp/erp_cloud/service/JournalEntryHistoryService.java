package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.JournalEntryHistoryDTO;
import com.erp.erp_cloud.dto.JournalEntryResponseDTO;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryHistory;
import com.erp.erp_cloud.repository.JournalEntryHistoryRepository;
import com.erp.erp_cloud.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bloque A (2026-11-21, F1-02 / T-01): version history of journal entries.
 *
 * Decision of the user (2026-11-21): an entry stays editable while its month
 * is open, but every change must keep the previous version complete. So
 * JournalEntryService calls {@link #record} right BEFORE it edits or annuls
 * an entry, inside the same transaction: if the change rolls back, so does
 * the snapshot, and a change can never be saved without its snapshot.
 */
@Service
@RequiredArgsConstructor
public class JournalEntryHistoryService {

    private static final Logger log = LoggerFactory.getLogger(JournalEntryHistoryService.class);

    private final JournalEntryHistoryRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Saves {@code before} (the entry as it is now, about to change) as one
     * version. Must run inside the caller's write transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(JournalEntry entry, JournalEntryResponseDTO before, String action) {
        String json;
        try {
            json = objectMapper.writeValueAsString(before);
        } catch (JsonProcessingException e) {
            // Never edit without a snapshot: fail the whole change instead.
            throw new IllegalStateException("Could not snapshot journal entry " + entry.getId(), e);
        }

        JournalEntryHistory row = new JournalEntryHistory();
        row.setCompanyId(entry.getCompany().getId());
        row.setJournalEntryId(entry.getId());
        row.setEntryVersion(entry.getVersion() != null ? entry.getVersion() : 0L);
        row.setAction(action);
        row.setSnapshot(json);
        row.setChangedAt(LocalDateTime.now());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            row.setChangedByUserId(principal.getId());
            row.setChangedBy(principal.getUsername());
        } else {
            row.setChangedBy("system");
        }

        repository.save(row);
        log.info("JOURNAL_ENTRY_VERSION_SAVED | entry: {} | version: {} | action: {} | by: {}",
                entry.getDocumentNumber(), row.getEntryVersion(), action, row.getChangedBy());
    }

    /**
     * Number of saved versions of each entry in {@code entryIds} (entries
     * without history are absent from the map). One query per list page.
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> countByEntry(Long companyId, java.util.Collection<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        for (Object[] row : repository.countByEntryIds(companyId, entryIds)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public long count(Long companyId, Long journalEntryId) {
        return repository.countByCompanyIdAndJournalEntryId(companyId, journalEntryId);
    }

    /** Saved versions of an entry, newest first. Caller checks the entry belongs to the company. */
    @Transactional(readOnly = true)
    public List<JournalEntryHistoryDTO> list(Long companyId, Long journalEntryId) {
        return repository.findByCompanyIdAndJournalEntryIdOrderByIdDesc(companyId, journalEntryId).stream()
                .map(row -> new JournalEntryHistoryDTO(
                        row.getId(),
                        row.getEntryVersion(),
                        row.getAction(),
                        row.getChangedBy(),
                        row.getChangedAt(),
                        readSnapshot(row)))
                .toList();
    }

    private JsonNode readSnapshot(JournalEntryHistory row) {
        try {
            return objectMapper.readTree(row.getSnapshot());
        } catch (JsonProcessingException e) {
            log.error("Unreadable journal entry snapshot, history id {}", row.getId(), e);
            return null;
        }
    }
}
