-- Withholding certificates (2026-11-07): generic, reusable table for
-- editable legal-document templates, scoped per company. Follows the
-- same philosophy already used for Tax.category/Tax.type (2026-10-15):
-- `template_key` is a stable, code-defined key ("WITHHOLDING_CERTIFICATE"
-- for this feature); `body_html`/`footer_html` are free text the
-- accountant edits from the UI, with {{variable}} placeholders resolved
-- by Mustache at render time -- never branched on in code. A company
-- with no row yet for a given key simply uses the built-in default
-- template (see LegalDocumentTemplateService); saving from the UI is
-- what creates the first row.
CREATE TABLE `legal_document_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `template_key` varchar(40) NOT NULL,
  `name` varchar(100) NOT NULL,
  `document_title` varchar(200) DEFAULT NULL,
  `body_html` longtext NOT NULL,
  `footer_html` longtext,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legal_template_company_key` (`company_id`,`template_key`),
  CONSTRAINT `fk_legal_template_company` FOREIGN KEY (`company_id`) REFERENCES `t_companies` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
