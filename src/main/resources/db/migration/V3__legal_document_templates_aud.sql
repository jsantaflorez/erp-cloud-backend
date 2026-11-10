-- Hibernate Envers audit shadow table for legal_document_templates
-- (2026-11-08): LegalDocumentTemplate is @Audited (it's editable legal
-- text with tax implications, worth tracking who changed what), so
-- Hibernate's startup schema-validation expects this table to exist
-- already -- missed in V2, which only created the main table. Mirrors
-- the exact shape already used for roles_aud/users_aud in the V1
-- baseline: REV/REVTYPE + revinfo FK, every audited business column
-- nullable (an audit row can represent a delete, which carries no
-- column values). `company_id` is intentionally absent here: the
-- entity marks that relation `@Audited(targetAuditMode = NOT_AUDITED)`,
-- so Envers never tracks it. The BaseEntity columns (created_at/
-- updated_at/created_by/updated_by) are absent too, same as every
-- other _aud table in this schema -- BaseEntity carries no @Audited
-- annotation of its own.
CREATE TABLE `legal_document_templates_aud` (
  `id` bigint NOT NULL,
  `REV` int NOT NULL,
  `REVTYPE` tinyint DEFAULT NULL,
  `template_key` varchar(40) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `document_title` varchar(200) DEFAULT NULL,
  `body_html` longtext,
  `footer_html` longtext,
  `active` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_legal_document_templates_aud_revinfo` (`REV`),
  CONSTRAINT `FK_legal_document_templates_aud_revinfo` FOREIGN KEY (`REV`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
