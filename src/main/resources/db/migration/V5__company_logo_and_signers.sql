-- Company logo + signature-block settings (2026-11-09) -- see the
-- "Especificación visual y de firmas" request and
-- claude/progreso-2026-11-09.md. Deliberately on t_companies, not on
-- legal_document_templates: a legal representative/accountant is an
-- attribute of the COMPANY, not of any one document template, so this
-- is set once and reused by this certificate and by any future legal
-- document (e.g. Balance General) without re-entering it per template.
--
-- `logo_url` was never actually used anywhere in the codebase (dead
-- column, confirmed by grep before this migration) and there is no
-- cloud storage bucket provisioned yet (see
-- claude/arquitectura-cloud-piloto-2026-11-05.md -- "nada desplegado
-- todavia"), so rather than host the image externally, it is stored
-- as a self-contained `data:image/...;base64,...` URI straight in the
-- column -- renders directly in both the admin preview and the PDF
-- with zero network fetch and zero new infrastructure or hosting
-- cost. varchar(255) could never hold that, hence the rename+retype
-- instead of leaving the old (unused) column alongside a new one.
--
-- Two signer slots, not a child table: the spec asks for "una o dos
-- columnas" (not N), and every other optional-attribute set already in
-- this schema (closing defaults, chart template) uses plain nullable
-- columns rather than a generic/child-table design. Each slot is
-- independently optional -- the certificate falls back to the old
-- unlabeled "REPRESENTANTE LEGAL" / "CONTADOR" lines when neither is
-- configured, so nothing already relying on that default breaks.
ALTER TABLE `t_companies`
  CHANGE COLUMN `logo_url` `logo_data_url` LONGTEXT DEFAULT NULL,
  ADD COLUMN `signer1_name` varchar(150) DEFAULT NULL AFTER `logo_data_url`,
  ADD COLUMN `signer1_role` varchar(100) DEFAULT NULL AFTER `signer1_name`,
  ADD COLUMN `signer1_id_number` varchar(30) DEFAULT NULL AFTER `signer1_role`,
  ADD COLUMN `signer1_professional_license` varchar(50) DEFAULT NULL AFTER `signer1_id_number`,
  ADD COLUMN `signer2_name` varchar(150) DEFAULT NULL AFTER `signer1_professional_license`,
  ADD COLUMN `signer2_role` varchar(100) DEFAULT NULL AFTER `signer2_name`,
  ADD COLUMN `signer2_id_number` varchar(30) DEFAULT NULL AFTER `signer2_role`,
  ADD COLUMN `signer2_professional_license` varchar(50) DEFAULT NULL AFTER `signer2_id_number`;
