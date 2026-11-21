-- V7 (2026-11-21) -- Bloque A, fase 1 de la auditoria del modulo contable
-- (docs/auditoria-modulo-contable-2026-11-19.md, F1-03 y F1-20).
--
-- 1) journal_entries.version / accounting_periods.version: bloqueo
--    optimista (@Version). Dos usuarios que editan el mismo comprobante:
--    el segundo en guardar recibe un aviso en vez de pisar al primero.
-- 2) journal_entries.idempotency_key: el cliente manda una llave por
--    comprobante (cabecera Idempotency-Key). Si la misma peticion llega dos
--    veces (doble Ctrl+Enter, reintento por red lenta), la segunda devuelve
--    el comprobante ya creado en vez de crear otro. Unica por compania;
--    NULL permitido (MySQL admite varios NULL en un indice UNIQUE).
ALTER TABLE `journal_entries`
  ADD COLUMN `version` bigint NOT NULL DEFAULT 0,
  ADD COLUMN `idempotency_key` varchar(64) DEFAULT NULL,
  ADD UNIQUE KEY `uk_journal_entries_company_idempotency` (`company_id`, `idempotency_key`);

ALTER TABLE `accounting_periods`
  ADD COLUMN `version` bigint NOT NULL DEFAULT 0;
