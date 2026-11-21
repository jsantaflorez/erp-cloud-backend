-- V8 (2026-11-21) -- Bloque A, fase 2 de la auditoria del modulo contable
-- (docs/auditoria-modulo-contable-2026-11-19.md, F1-02 / T-01).
--
-- Historial de versiones de los comprobantes. Decision del usuario
-- (2026-11-21): un comprobante se puede editar mientras su mes este
-- abierto, pero cada cambio debe conservar la version anterior completa.
-- Antes de cada edicion o anulacion se guarda aqui una foto (JSON) del
-- comprobante tal como estaba: encabezado y todas sus lineas, con quien y
-- cuando lo cambio.
--
-- Solo se inserta (la aplicacion nunca hace UPDATE ni DELETE aqui). La FK
-- compuesta (company_id, journal_entry_id) impide que una foto quede
-- asociada a un comprobante de otra compania.
CREATE TABLE `journal_entry_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `journal_entry_id` bigint NOT NULL,
  `entry_version` bigint NOT NULL,
  `action` varchar(20) NOT NULL,
  `snapshot` longtext NOT NULL,
  `changed_by_user_id` bigint DEFAULT NULL,
  `changed_by` varchar(100) DEFAULT NULL,
  `changed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_journal_entry_history_entry` (`company_id`, `journal_entry_id`, `id`),
  CONSTRAINT `fk_journal_entry_history_entry`
    FOREIGN KEY (`company_id`, `journal_entry_id`) REFERENCES `journal_entries` (`company_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
