-- 2026-10-31 -- New company: "UNIDAD INMOBILIARIA CERRADA NAVARRA"
-- (NIT 801004463), requested by the user to have an empty company
-- available for testing -- in particular the new SIEWIN legacy PUC
-- importer (see propuesta-importador-puc-siewin-2026-10-31.md), since
-- every company already in the database now has a Plan de Cuentas.
--
-- No company-creation endpoint exists yet (see ChartTemplateType's own
-- javadoc and CompanyController, which is read-only), so this is a
-- direct-SQL provisioning, same convention as
-- 2026-10-28_seed_multiempresa_test_user.sql and
-- 2026-10-29_add_company5_to_multiempresa_test.sql: clone whatever
-- fields we can't safely guess (city_id) from company 4, clone
-- admin@erpcloud.com's company-4 roles + permissions onto the new
-- company, and grant admin@erpcloud.com access to it. Run this by hand
-- against the local dev database. Safe to re-run -- every INSERT is
-- NOT EXISTS-guarded.
--
-- Values below the user explicitly gave: legal_name, tax_id.
-- Values computed, not invented: verification_digit = 0, the real
-- Colombian DV for NIT 801004463 (modulo-11 algorithm, weights
-- 3,7,13,17,19,23,29,37,41).
-- Values invented (user said to make them up) -- CHANGE THESE if you
-- want different ones, there is no edit-company screen yet so for now
-- that also means editing this script and re-running, or a follow-up
-- SQL UPDATE:
--   trade_name    -> 'Navarra'
--   address       -> 'Carrera 1 # 1-01' (placeholder)
--   phone         -> '(601) 000-0000' (placeholder)
--   email         -> 'administracion@uicnavarra.test' (.test domain,
--                     same placeholder convention as Piloto 2's own
--                     'contacto@pilotodos.test')
--   tenant_id     -> 'uic-navarra' (must stay unique)
-- Value deliberately chosen, not random -- tax_regime: SPECIAL_REGIME.
-- A "unidad inmobiliaria cerrada" / propiedad horizontal administration
-- entity is a non-profit entity under Colombian tax law ("Entidades sin
-- animo de lucro", exactly what TaxRegime.SPECIAL_REGIME's own comment
-- describes) -- the closest fit among the 6 existing values, not a
-- commercial VAT_REGISTERED/VAT_NOT_REGISTERED business. Change it if
-- this company's real tax situation is different.
--
-- chart_template is deliberately left NULL -- this company's PUC will
-- come from the SIEWIN legacy importer, which never sets
-- Company.chartTemplate (see SiewinPucImportService's class javadoc):
-- its own numbering doesn't correspond to either the COMERCIAL or
-- SOLIDARIO reference template.

START TRANSACTION;

-- 1) New company. city_id is cloned from company 4 (a real, valid
--    city_id already in the database) since there's no reliable way to
--    pick a specific city from here -- change it with a follow-up
--    UPDATE t_companies SET city_id = ... if this company is actually
--    in a different city.
INSERT INTO t_companies
  (legal_name, trade_name, tax_id, verification_digit, tax_regime,
   city_id, address, phone, email, tenant_id, active, logo_url,
   chart_template, default_gain_account_id, default_loss_account_id)
SELECT
  'UNIDAD INMOBILIARIA CERRADA NAVARRA', 'Navarra', '801004463', '0',
  'SPECIAL_REGIME', city_id, 'Carrera 1 # 1-01', '(601) 000-0000',
  'administracion@uicnavarra.test', 'uic-navarra', 1, NULL,
  NULL, NULL, NULL
FROM t_companies
WHERE company_id = 4
  AND NOT EXISTS (SELECT 1 FROM t_companies WHERE tenant_id = 'uic-navarra')
  AND NOT EXISTS (SELECT 1 FROM t_companies WHERE tax_id = '801004463');

-- 2) Clone admin@erpcloud.com's company-4 role codes onto the new
--    company (roles are company-scoped -- uk_role_name_company).
INSERT INTO roles (company_id, name, code, system_role)
SELECT nc.company_id, r.name, r.code, r.system_role
FROM roles r
JOIN user_roles ur ON ur.role_id = r.id
JOIN users u ON u.id = ur.user_id
JOIN t_companies nc ON nc.tenant_id = 'uic-navarra'
WHERE u.email = 'admin@erpcloud.com'
  AND ur.company_id = 4
  AND NOT EXISTS (
    SELECT 1 FROM roles r2 WHERE r2.company_id = nc.company_id AND r2.code = r.code
  );

-- 3) Same permissions on each new role as its company-4 counterpart.
INSERT INTO role_permissions (role_id, permission_id)
SELECT new_role.id, rp.permission_id
FROM role_permissions rp
JOIN roles old_role ON old_role.id = rp.role_id
JOIN user_roles ur ON ur.role_id = old_role.id
JOIN users u ON u.id = ur.user_id
JOIN t_companies nc ON nc.tenant_id = 'uic-navarra'
JOIN roles new_role ON new_role.company_id = nc.company_id AND new_role.code = old_role.code
WHERE u.email = 'admin@erpcloud.com'
  AND ur.company_id = 4
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = new_role.id AND rp2.permission_id = rp.permission_id
  );

-- 4) Grant admin@erpcloud.com UserRole rows in the new company, one
--    per role cloned in step 2.
INSERT INTO user_roles (user_id, role_id, company_id, assigned_at, created_at, updated_at, created_by, updated_by)
SELECT u.id, new_role.id, nc.company_id, NOW(), NOW(), NOW(), u.id, u.id
FROM roles new_role
JOIN t_companies nc ON nc.tenant_id = 'uic-navarra' AND nc.company_id = new_role.company_id
JOIN users u ON u.email = 'admin@erpcloud.com'
WHERE NOT EXISTS (
  SELECT 1 FROM user_roles ur3 WHERE ur3.user_id = u.id AND ur3.role_id = new_role.id AND ur3.company_id = nc.company_id
);

COMMIT;

-- Verification: should now show company 4 (and whatever others you
-- already had) plus the new UNIDAD INMOBILIARIA CERRADA NAVARRA
-- company, with the same set of role codes as company 4.
SELECT c.company_id, c.legal_name, c.tax_id, c.verification_digit, c.chart_template, r.code
FROM user_roles ur
JOIN users u ON u.id = ur.user_id
JOIN t_companies c ON c.company_id = ur.company_id
JOIN roles r ON r.id = ur.role_id
WHERE u.email = 'admin@erpcloud.com'
ORDER BY c.company_id, r.code;
