package com.erp.erp_cloud.service;

/**
 * Fase 4 (2026-11-24): shared helpers for the selector lookups
 * ({@code /chart-of-accounts/lookup}, {@code /third-parties/lookup}).
 *
 * The queries use {@code LIKE ... ESCAPE '!'} -- '!' rather than the
 * usual backslash, because MySQL also treats a backslash inside a string
 * literal as an escape character and {@code ESCAPE '\'} breaks there.
 */
public final class LookupText {

    /** Hard cap for one lookup: the selectors show at most this many rows. */
    public static final int MAX_LIMIT = 50;

    private LookupText() {
    }

    /** Lower-cased, trimmed search text; never null. */
    public static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase();
    }

    /** Escapes the LIKE wildcards so the user's text matches literally. */
    public static String escapeLike(String normalized) {
        return normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public static String prefix(String normalized) {
        return escapeLike(normalized) + "%";
    }

    public static String contains(String normalized) {
        return "%" + escapeLike(normalized) + "%";
    }

    /** Clamps the requested limit to 1..MAX_LIMIT (null means MAX_LIMIT). */
    public static int clampLimit(Integer limit) {
        if (limit == null) return MAX_LIMIT;
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }
}
