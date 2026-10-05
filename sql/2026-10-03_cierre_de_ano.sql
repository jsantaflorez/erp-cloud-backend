-- ============================================================
-- Cierre de Año: closing entry accounts + opening-balance
-- snapshot table. Run manually against MySQL -- this project
-- has no Flyway/Liquibase and uses ddl-auto=validate.
-- ============================================================

-- 1) Company-level SUGGESTED Ganancia/Pérdida accounts, used only
--    to pre-fill the year-end closing screen's dropdowns. Both
--    nullable: a company with no defaults configured yet simply
--    shows no suggestion, the user must pick explicitly.
ALTER TABLE t_companies
    ADD COLUMN default_gain_account_id BIGINT NULL,
    ADD COLUMN default_loss_account_id BIGINT NULL,
    ADD CONSTRAINT fk_company_default_gain_account
        FOREIGN KEY (default_gain_account_id) REFERENCES chart_of_accounts (id),
    ADD CONSTRAINT fk_company_default_loss_account
        FOREIGN KEY (default_loss_account_id) REFERENCES chart_of_accounts (id);

-- 2) The Ganancia/Pérdida accounts and closing entry ACTUALLY used
--    for a given year's close -- only ever populated on the
--    month=12 record of a year, the same record that already
--    carries is_year_close. Confirmed per-year, never silently
--    inherited from the company default above.
ALTER TABLE accounting_periods
    ADD COLUMN gain_account_id BIGINT NULL,
    ADD COLUMN loss_account_id BIGINT NULL,
    ADD COLUMN closing_entry_id BIGINT NULL,
    ADD CONSTRAINT fk_period_gain_account
        FOREIGN KEY (gain_account_id) REFERENCES chart_of_accounts (id),
    ADD CONSTRAINT fk_period_loss_account
        FOREIGN KEY (loss_account_id) REFERENCES chart_of_accounts (id),
    ADD CONSTRAINT fk_period_closing_entry
        FOREIGN KEY (closing_entry_id) REFERENCES journal_entries (id);

-- 3) Opening-balance snapshot per account x third party x cost
--    center, for a given fiscal year. Regenerated in full every
--    time the PRIOR year is closed. No unique constraint on the
--    logical key (company, year, account, third_party, cost_center)
--    -- MySQL treats each NULL as distinct for uniqueness purposes,
--    so it would not actually enforce "one row per account with no
--    tercero/cc" anyway; the real uniqueness guarantee is the
--    regeneration algorithm itself (always deletes a year's rows
--    before re-inserting), not a DB constraint. See
--    AccountOpeningBalance.java for the full rationale.
CREATE TABLE account_opening_balances (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    year INT NOT NULL,
    account_id BIGINT NOT NULL,
    third_party_id BIGINT NULL,
    cost_center_id BIGINT NULL,
    balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_opening_balance_lookup (company_id, year, account_id),
    CONSTRAINT fk_opening_balance_company
        FOREIGN KEY (company_id) REFERENCES t_companies (company_id),
    CONSTRAINT fk_opening_balance_account
        FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_opening_balance_third_party
        FOREIGN KEY (third_party_id) REFERENCES third_parties (id),
    CONSTRAINT fk_opening_balance_cost_center
        FOREIGN KEY (cost_center_id) REFERENCES cost_centers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4) The "CIERRE" DocumentType is NOT created here -- the backend
--    auto-creates it per company, idempotently, the first time that
--    company closes a year (DocumentTypeService.findOrCreateClosingDocumentType).
--    Nothing to run manually for that part.
