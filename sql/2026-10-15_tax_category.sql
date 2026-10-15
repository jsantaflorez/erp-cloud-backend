-- ============================================================
-- Reemplaza el enfoque de "Tax.type como enum fijo" (planteado y
-- construido el 2026-10-14, nunca commiteado, superado el mismo
-- 2026-10-15 a pedido del usuario: el catalogo de impuestos no debe
-- quedar atado a un grupo cerrado de tipos en codigo duro, para
-- blindar el sistema ante reformas tributarias futuras -- IVA a
-- bebidas azucaradas, impuesto al carbono, a plasticos, etc.).
--
-- Disena en dos campos independientes:
--   - type (texto libre, SIN restriccion): el nombre fiscal especifico
--     ("RETEFUENTE", "IMPOCONSUMO", "IMP_CARBONO", lo que sea). Nunca
--     se usa en un if/switch del backend -- JournalEntryService.
--     handleTax() (la contabilizacion real del comprobante) jamas lo
--     lee. Solo es informativo / para subagrupar reportes.
--   - category (3 valores fijos, estructurales): RETENCION / IVA /
--     OTRO -- esto es lo que de verdad usan los reportes fiscales
--     (Informe de IVA / Informe de Retenciones, Fase 3/4) para saber
--     a que reporte alimentar. IVA Generado vs Descontable se separa
--     con el campo `sign` (C/D) que ya existe, no con un cuarto valor
--     de categoria.
--
-- Run manually against MySQL -- this project has no
-- Flyway/Liquibase and uses ddl-auto=validate.
-- ============================================================

-- 1. Ensancha `type` (era VARCHAR(20) bajo el enfoque de enum del
--    2026-10-14) para dar margen a nombres futuros mas largos
--    ("IMPUESTO_BOLSAS_PLASTICAS", etc.) -- sigue siendo texto libre,
--    sin ningun CHECK/enum real a nivel de base de datos.
ALTER TABLE taxes MODIFY COLUMN type VARCHAR(30) NOT NULL;

-- 2. Agrega `category`, con un default seguro ('OTRO') mientras se
--    reclasifica cada fila existente abajo.
ALTER TABLE taxes
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'OTRO';

-- 3. Reclasifica filas existentes a partir de lo que ya haya en
--    `type` -- funciona igual si esa columna todavia tiene el texto
--    libre original (anterior al intento de enum del 2026-10-14) o ya
--    quedo con los 5 valores de ese enum (IVA_GENERADO,
--    IVA_DESCONTABLE, RETEFUENTE, RETEICA, RETEIVA), porque nunca se
--    llego a aplicar sql/2026-10-14_tax_type_enum.sql (se descarto) o
--    porque sí se aplico -- ambos casos coinciden con estos patrones.
UPDATE taxes SET category = 'RETENCION'
    WHERE UPPER(type) IN ('ICA', 'RETE_ICA', 'RETEICA',
                           'RETIVA', 'RETE_IVA', 'RETEIVA',
                           'RETEFUENTE', 'RETE_FUENTE', 'RETENCION_FUENTE')
       OR UPPER(type) LIKE 'RETE%';

UPDATE taxes SET category = 'IVA'
    WHERE category = 'OTRO' AND UPPER(type) LIKE '%IVA%';

-- Cualquier otra fila (IMPOCONSUMO, IMP_SALUDABLE, IMP_CARBONO, o
-- cualquier nombre no reconocido) se queda en 'OTRO' por el DEFAULT
-- de arriba -- revisa manualmente en la pantalla "Impuestos" si algo
-- quedo mal clasificado.
