-- =============================================================================
-- 2026-11-02 -- Audit pre-production (findings A3 + M5)
--
-- Run ONCE against erpdb, in order, BEFORE starting the backend built from
-- branch audit/prod-readiness (JournalEntryItem now maps journal_entry_items.company_id
-- and ddl-auto=validate will refuse to start without it).
--
--   mysql -h 127.0.0.1 -P 3308 -u root -p erpdb < sql/2026-11-02_tenant_integrity_and_indexes.sql
--
-- Safe to re-run: every index/constraint is created only if missing.
--
-- Part 1  Makes sure every index/unique key declared with @Index/@UniqueConstraint
--         in the JPA entities really exists (ddl-auto=validate never creates them).
-- Part 2  journal_entries (company_id, document_number) for findByCompanyIdAndDocumentNumber.
-- Part 3  Tenant-consistent foreign keys: a journal entry line can only point to an
--         entry, account, third party, cost center or tax of the SAME company.
-- =============================================================================

DROP PROCEDURE IF EXISTS erp_add_index_if_missing;
DROP PROCEDURE IF EXISTS erp_add_fk_if_missing;

DELIMITER $$
CREATE PROCEDURE erp_add_index_if_missing(IN p_table VARCHAR(64), IN p_index VARCHAR(64),
                                          IN p_cols VARCHAR(255), IN p_unique TINYINT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', IF(p_unique = 1, 'UNIQUE ', ''),
                          'INDEX `', p_index, '` (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        SELECT CONCAT('created ', p_table, '.', p_index) AS result;
    END IF;
END$$

CREATE PROCEDURE erp_add_fk_if_missing(IN p_table VARCHAR(64), IN p_fk VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                   WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = p_table
                     AND CONSTRAINT_NAME = p_fk AND CONSTRAINT_TYPE = 'FOREIGN KEY') THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        SELECT CONCAT('created FK ', p_table, '.', p_fk) AS result;
    END IF;
END$$
DELIMITER ;

-- -----------------------------------------------------------------------------
-- Part 1 -- indexes declared in the entities
-- -----------------------------------------------------------------------------
CALL erp_add_index_if_missing('account_opening_balances', 'idx_opening_balance_lookup', 'company_id, year, account_id', 0);
CALL erp_add_index_if_missing('accounting_periods', 'uk_company_year_month', 'company_id, year, month', 1);
CALL erp_add_index_if_missing('accounting_periods', 'idx_period_year_hierarchy', 'company_id, year, is_year_close', 0);
CALL erp_add_index_if_missing('accounting_periods', 'idx_period_company_open', 'company_id, is_open', 0);
CALL erp_add_index_if_missing('chart_account_templates', 'idx_chart_account_templates_type_code', 'template_type, code', 0);
CALL erp_add_index_if_missing('chart_of_accounts', 'uk_company_code', 'company_id, code', 1);
CALL erp_add_index_if_missing('chart_of_accounts', 'idx_coa_parent', 'company_id, parent_id', 0);
CALL erp_add_index_if_missing('chart_of_accounts', 'idx_coa_posting', 'company_id, posting_account, active', 0);
CALL erp_add_index_if_missing('chart_of_accounts', 'idx_coa_level', 'company_id, level, active', 0);
CALL erp_add_index_if_missing('chart_of_accounts', 'idx_coa_name', 'company_id, name', 0);
CALL erp_add_index_if_missing('chart_of_accounts', 'idx_coa_class', 'company_id, account_class', 0);
CALL erp_add_index_if_missing('chart_of_accounts', 'idx_coa_category', 'company_id, account_category', 0);
CALL erp_add_index_if_missing('chart_of_accounts', 'idx_coa_statement', 'company_id, financial_statement', 0);
CALL erp_add_index_if_missing('chart_of_accounts', 'idx_coa_year_end', 'company_id, closes_at_year_end', 0);
CALL erp_add_index_if_missing('cost_centers', 'uk_company_cost_center_code', 'company_id, code', 1);
CALL erp_add_index_if_missing('document_types', 'uk_document_type_company_code', 'company_id, code', 1);
CALL erp_add_index_if_missing('journal_entries', 'idx_journal_entries_annulled_status', 'company_id, is_annulled', 0);
CALL erp_add_index_if_missing('journal_entries', 'idx_journal_entries_date_lookup', 'company_id, entry_date', 0);
CALL erp_add_index_if_missing('journal_entries', 'uk_company_doctype_consecutive_active', 'company_id, document_type_id, consecutive, is_active', 1);
CALL erp_add_index_if_missing('roles', 'uk_role_name_company', 'name, company_id', 1);
CALL erp_add_index_if_missing('taxes', 'uk_tax_company_code', 'company_id, code', 1);
CALL erp_add_index_if_missing('taxes', 'uk_tax_company_account', 'company_id, account_id', 1);
CALL erp_add_index_if_missing('third_parties', 'uk_company_document', 'company_id, document_number', 1);
CALL erp_add_index_if_missing('third_parties', 'idx_third_party_business_name', 'company_id, business_name', 0);
CALL erp_add_index_if_missing('third_parties', 'idx_third_party_trade_name', 'company_id, trade_name', 0);
CALL erp_add_index_if_missing('third_parties', 'idx_third_party_names', 'company_id, first_name, last_name', 0);
CALL erp_add_index_if_missing('third_parties', 'idx_third_party_doc_type', 'company_id, document_type', 0);
CALL erp_add_index_if_missing('third_parties', 'idx_third_party_active', 'company_id, active', 0);
CALL erp_add_index_if_missing('users', 'idx_user_email', 'email', 0);
CALL erp_add_index_if_missing('user_roles', 'idx_user_company_role', 'user_id, company_id', 0);

-- -----------------------------------------------------------------------------
-- Part 2 -- lookup by document number inside a company
-- -----------------------------------------------------------------------------
CALL erp_add_index_if_missing('journal_entries', 'idx_journal_entries_company_docnum', 'company_id, document_number', 0);

-- -----------------------------------------------------------------------------
-- Part 3 -- tenant-consistent foreign keys for journal_entry_items
-- -----------------------------------------------------------------------------
-- 3a) Pre-check: every count below MUST be 0. If any is not, STOP and review
--     those rows: they are lines pointing to another company's data.
SELECT 'account of another company' AS check_name, COUNT(*) AS bad_rows
FROM journal_entry_items i JOIN journal_entries e ON e.id = i.journal_entry_id
JOIN chart_of_accounts a ON a.id = i.account_id WHERE a.company_id <> e.company_id
UNION ALL
SELECT 'third party of another company', COUNT(*)
FROM journal_entry_items i JOIN journal_entries e ON e.id = i.journal_entry_id
JOIN third_parties t ON t.id = i.third_party_id WHERE t.company_id <> e.company_id
UNION ALL
SELECT 'cost center of another company', COUNT(*)
FROM journal_entry_items i JOIN journal_entries e ON e.id = i.journal_entry_id
JOIN cost_centers c ON c.id = i.cost_center_id WHERE c.company_id <> e.company_id
UNION ALL
SELECT 'tax of another company', COUNT(*)
FROM journal_entry_items i JOIN journal_entries e ON e.id = i.journal_entry_id
JOIN taxes x ON x.id = i.tax_id WHERE x.company_id <> e.company_id;

-- 3b) Denormalized tenant column on the lines, backfilled from the header.
SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'journal_entry_items' AND COLUMN_NAME = 'company_id');
SET @ddl = IF(@has_col = 0, 'ALTER TABLE journal_entry_items ADD COLUMN company_id BIGINT NULL AFTER id', 'SELECT ''company_id already present''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE journal_entry_items i JOIN journal_entries e ON e.id = i.journal_entry_id
SET i.company_id = e.company_id
WHERE i.company_id IS NULL OR i.company_id <> e.company_id;

ALTER TABLE journal_entry_items MODIFY company_id BIGINT NOT NULL;

-- 3c) (company_id, id) unique keys on the referenced tables (required as FK targets).
CALL erp_add_index_if_missing('journal_entries',   'uk_journal_entries_company_id',   'company_id, id', 1);
CALL erp_add_index_if_missing('chart_of_accounts', 'uk_chart_of_accounts_company_id', 'company_id, id', 1);
CALL erp_add_index_if_missing('third_parties',     'uk_third_parties_company_id',     'company_id, id', 1);
CALL erp_add_index_if_missing('cost_centers',      'uk_cost_centers_company_id',      'company_id, id', 1);
CALL erp_add_index_if_missing('taxes',             'uk_taxes_company_id',             'company_id, id', 1);

-- 3d) Composite FKs (MATCH SIMPLE: a NULL third_party/cost_center/tax is allowed).
--     No ON DELETE CASCADE on purpose: accounting rows are never deleted in cascade.
CALL erp_add_fk_if_missing('journal_entry_items', 'fk_jei_tenant_entry',
  'ALTER TABLE journal_entry_items ADD CONSTRAINT fk_jei_tenant_entry FOREIGN KEY (company_id, journal_entry_id) REFERENCES journal_entries (company_id, id)');
CALL erp_add_fk_if_missing('journal_entry_items', 'fk_jei_tenant_account',
  'ALTER TABLE journal_entry_items ADD CONSTRAINT fk_jei_tenant_account FOREIGN KEY (company_id, account_id) REFERENCES chart_of_accounts (company_id, id)');
CALL erp_add_fk_if_missing('journal_entry_items', 'fk_jei_tenant_third_party',
  'ALTER TABLE journal_entry_items ADD CONSTRAINT fk_jei_tenant_third_party FOREIGN KEY (company_id, third_party_id) REFERENCES third_parties (company_id, id)');
CALL erp_add_fk_if_missing('journal_entry_items', 'fk_jei_tenant_cost_center',
  'ALTER TABLE journal_entry_items ADD CONSTRAINT fk_jei_tenant_cost_center FOREIGN KEY (company_id, cost_center_id) REFERENCES cost_centers (company_id, id)');
CALL erp_add_fk_if_missing('journal_entry_items', 'fk_jei_tenant_tax',
  'ALTER TABLE journal_entry_items ADD CONSTRAINT fk_jei_tenant_tax FOREIGN KEY (company_id, tax_id) REFERENCES taxes (company_id, id)');

DROP PROCEDURE IF EXISTS erp_add_index_if_missing;
DROP PROCEDURE IF EXISTS erp_add_fk_if_missing;

-- Verification: list every index per table
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
ORDER BY TABLE_NAME, INDEX_NAME;
