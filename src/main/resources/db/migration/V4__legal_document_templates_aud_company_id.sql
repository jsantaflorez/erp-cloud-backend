-- Fixes V3__legal_document_templates_aud.sql (2026-11-08): that migration
-- deliberately left out `company_id`, reasoning that
-- `@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)` meant
-- the relationship itself was excluded from the audit table. Wrong --
-- confirmed against `Role.company`, the exact same annotation
-- combination already in production, whose `roles_aud` DOES carry a
-- `company_id` column. NOT_AUDITED only means Envers won't keep a
-- versioned history of the related Company row itself (no join back to
-- a historicized company row) -- the owning entity's own `company_id`
-- value is still recorded as a plain column, like any other audited
-- field. V3 already ran (and is recorded in flyway_schema_history), so
-- this is a follow-up ALTER rather than an edit to that file.
ALTER TABLE `legal_document_templates_aud`
  ADD COLUMN `company_id` bigint DEFAULT NULL;
