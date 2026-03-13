package com.erp.erp_cloud.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "accounting_periods",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_year_month",
                        columnNames = {"company_id", "year", "month"}
                )
        },
        indexes = {
                // For querying open periods
                @Index(name = "idx_period_company_open", columnList = "company_id, is_open"),
                // For date range queries
                @Index(name = "idx_period_company_year_month", columnList = "company_id, year, month")
        }
)
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AccountingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer month; // 1 to 12

    @Column(name = "is_open", nullable = false)
    private boolean isOpen = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    // --- AUDIT FIELDS ---

    /**
     * Timestamp when the period was closed.
     * Null if the period is still open.
     */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * User who closed the period.
     * Important for audit trail and accountability.
     */
    @Column(name = "closed_by", length = 100)
    private String closedBy;

    /**
     * Optional notes explaining why the period was closed.
     * E.g., "Month-end closing completed", "Audited and approved"
     */
    @Column(name = "closing_notes", length = 500)
    private String closingNotes;

    /**
     * Timestamp when the period was last reopened (if applicable).
     * Useful for tracking re-openings for adjustments.
     */
    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    /**
     * User who reopened the period.
     */
    @Column(name = "reopened_by", length = 100)
    private String reopenedBy;

    /**
     * Optional notes explaining why the period was reopened.
     * E.g., "Adjusting entry required", "Error correction"
     */
    @Column(name = "reopening_notes", length = 500)
    private String reopeningNotes;

    // --- BUSINESS METHODS ---

    /**
     * Returns a human-readable period identifier.
     * @return Period string in format "YYYY-MM"
     */
    public String getPeriodCode() {
        return String.format("%d-%02d", year, month);
    }

    /**
     * Validates that year and month are within valid ranges.
     */
    @PrePersist
    @PreUpdate
    private void validatePeriod() {
        if (year == null || year < 1900 || year > 2100) {
            throw new IllegalArgumentException("Year must be between 1900 and 2100");
        }
        if (month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
    }
}