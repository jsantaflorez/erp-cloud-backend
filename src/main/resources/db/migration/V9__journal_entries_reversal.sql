-- V9 (2026-11-24) -- Bloque A, fase 3 de la auditoria del modulo contable
-- (docs/auditoria-modulo-contable-2026-11-19.md, A1): anulacion por reverso.
--
-- Antes, anular un documento ponia en cero sus debitos y creditos: el
-- original perdia sus valores. Ahora el original queda intacto (marcado
-- anulado) y el sistema crea un documento de reverso del tipo reservado
-- ANULACION (prefijo ANU, consecutivo propio), con la MISMA fecha y los
-- debitos y creditos intercambiados, que cancela su efecto.
--
--  - reversal_entry_id: en el ORIGINAL, apunta a su reverso.
--  - reversal_of_id:    en el REVERSO, apunta al original. UNIQUE: un
--                       documento tiene como maximo un reverso.
--
-- Los procesos del sistema (recalculo del CIERRE, reabrir el ano, reemplazo
-- de la APERTURA de SIEWIN) siguen anulando como antes, en ceros, sin
-- reverso (decision del usuario, 2026-11-24). Los documentos anulados antes
-- de esta version quedan como estan (datos de prueba).
ALTER TABLE `journal_entries`
  ADD COLUMN `reversal_entry_id` bigint DEFAULT NULL,
  ADD COLUMN `reversal_of_id` bigint DEFAULT NULL,
  ADD UNIQUE KEY `uk_journal_entries_reversal_of` (`reversal_of_id`),
  ADD CONSTRAINT `fk_journal_entries_reversal_entry`
      FOREIGN KEY (`reversal_entry_id`) REFERENCES `journal_entries` (`id`),
  ADD CONSTRAINT `fk_journal_entries_reversal_of`
      FOREIGN KEY (`reversal_of_id`) REFERENCES `journal_entries` (`id`);
