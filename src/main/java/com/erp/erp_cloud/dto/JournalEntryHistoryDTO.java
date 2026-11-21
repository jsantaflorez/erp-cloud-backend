package com.erp.erp_cloud.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * Bloque A (2026-11-21): one saved version of a journal entry, as returned by
 * GET /api/v1/journal-entries/{id}/history. {@code snapshot} is the entry as
 * it was before {@code action} (same shape as JournalEntryResponseDTO when it
 * was saved), kept as JSON so old versions still load if the DTO changes.
 */
public record JournalEntryHistoryDTO(
        Long id,
        Long entryVersion,
        String action,
        String changedBy,
        LocalDateTime changedAt,
        JsonNode snapshot
) {}
