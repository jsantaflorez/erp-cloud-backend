-- =============================================================================
-- V6 -- document numbers are unique PER COMPANY, not across the whole system.
--
-- V1 kept a Hibernate-generated global UNIQUE on journal_entries.document_number
-- (from @Column(unique = true)). Document numbers are prefix + consecutive
-- ("FV-1"), so two companies using the same prefix collided: the second
-- company's "FV-1" failed with a duplicate-key error.
--
-- Replaced by UNIQUE (company_id, document_number), which also takes over the
-- plain lookup index idx_journal_entries_company_docnum on the same columns.
-- Safe on existing data: the old global rule is stricter, so no company can
-- already hold a duplicate number.
-- =============================================================================
ALTER TABLE `journal_entries`
  ADD UNIQUE KEY `uk_journal_entries_company_docnum` (`company_id`, `document_number`),
  DROP INDEX `UKd1yqsab0vvb6rcf33n46e9gcr`,
  DROP INDEX `idx_journal_entries_company_docnum`;
