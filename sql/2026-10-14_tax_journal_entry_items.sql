-- ============================================================
-- Vincula el catalogo de Impuestos a los renglones de un
-- comprobante: cuando la cuenta elegida en una linea tiene un
-- impuesto configurado (tabla "taxes", uk_tax_company_account),
-- el backend calcula el debito/credito de esa linea a partir de
-- una base gravable digitada, y guarda ambos valores para que
-- los reportes fiscales (IVA, Retenciones) no tengan que
-- recalcular la base a partir del valor ya contabilizado.
--
-- Run manually against MySQL -- this project has no
-- Flyway/Liquibase and uses ddl-auto=validate.
-- ============================================================

ALTER TABLE journal_entry_items
    ADD COLUMN tax_id BIGINT NULL,
    ADD COLUMN tax_base DECIMAL(18, 2) NULL,
    ADD CONSTRAINT fk_journal_entry_item_tax
        FOREIGN KEY (tax_id) REFERENCES taxes (id);
