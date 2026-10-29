-- 2026-10-28 -- Seed script: test user with access to TWO companies,
-- for manually testing the Multiempresa Fase 2 company switcher
-- (AppHeader.jsx dropdown + POST /api/v1/session/switch-company).
--
-- Run this by hand against the local dev database (same convention as
-- every other file in this folder -- no Flyway/Liquibase in this
-- project). Safe to re-run: every INSERT is guarded with NOT EXISTS.
--
-- Design: rather than guessing values for columns we can't see from
-- here (city_id, tax_regime, which permissions a role has...), every
-- INSERT below clones real rows already in this database:
--   * the new company clones company_id = 4's shared fields (city,
--     tax regime, chart template), only identity fields differ.
--   * the new user clones admin@erpcloud.com's password_hash exactly,
--     so it logs in with the SAME password you already use for admin
--     -- this script never needs to know or set a plaintext password.
--   * the new company's roles + permissions + the new user's
--     UserRole rows all clone admin@erpcloud.com's existing setup for
--     company 4.
--
-- Change the two identifying values below if you want different
-- ones -- tenant_id and the new user's email just need to not already
-- exist.

START TRANSACTION;

-- 1) New company, cloned from company 4's shared fields.
--    default_gain_account_id/default_loss_account_id are deliberately
--    left NULL: those point at ChartOfAccounts rows that belong to
--    company 4 specifically and must never cross tenants.
INSERT INTO t_companies
  (legal_name, trade_name, tax_id, verification_digit, tax_regime,
   city_id, address, phone, email, tenant_id, active, logo_url,
   chart_template, default_gain_account_id, default_loss_account_id)
SELECT
  'Empresa Piloto 2 S.A.S.', 'Piloto 2', '900888777', '1',
  tax_regime, city_id, address, phone,
  'contacto@pilotodos.test', 'empresa-piloto-2', 1, logo_url,
  chart_template, NULL, NULL
FROM t_companies
WHERE company_id = 4
  AND NOT EXISTS (SELECT 1 FROM t_companies WHERE tenant_id = 'empresa-piloto-2');

-- 2) New test user, cloned from admin@erpcloud.com -- SAME password
--    hash, so "whatever password already logs admin in" also logs
--    this user in. Only identity fields differ.
INSERT INTO users
  (email, password_hash, first_name, last_name, active, email_verified,
   last_login_at, password_changed_at, failed_login_attempts,
   locked_until, mfa_enabled, mfa_secret, created_at, updated_at,
   created_by, updated_by)
SELECT
  'contador.multiempresa@erpcloud.com', password_hash,
  'Contador', 'Multiempresa', 1, email_verified,
  NULL, password_changed_at, 0,
  NULL, mfa_enabled, mfa_secret, NOW(), NOW(),
  created_by, updated_by
FROM users
WHERE email = 'admin@erpcloud.com'
  AND NOT EXISTS (SELECT 1 FROM users WHERE email = 'contador.multiempresa@erpcloud.com');

-- 3) One role per role admin@erpcloud.com already has for company 4,
--    cloned onto the new company (roles are company-scoped --
--    uk_role_name_company).
INSERT INTO roles (company_id, name, code, system_role)
SELECT nc.company_id, r.name, r.code, r.system_role
FROM roles r
JOIN user_roles ur ON ur.role_id = r.id
JOIN users u ON u.id = ur.user_id
JOIN t_companies nc ON nc.tenant_id = 'empresa-piloto-2'
WHERE u.email = 'admin@erpcloud.com'
  AND ur.company_id = 4
  AND NOT EXISTS (
    SELECT 1 FROM roles r2 WHERE r2.company_id = nc.company_id AND r2.code = r.code
  );

-- 4) Same permissions on each new role as its company-4 counterpart.
INSERT INTO role_permissions (role_id, permission_id)
SELECT new_role.id, rp.permission_id
FROM role_permissions rp
JOIN roles old_role ON old_role.id = rp.role_id
JOIN user_roles ur ON ur.role_id = old_role.id
JOIN users u ON u.id = ur.user_id
JOIN t_companies nc ON nc.tenant_id = 'empresa-piloto-2'
JOIN roles new_role ON new_role.company_id = nc.company_id AND new_role.code = old_role.code
WHERE u.email = 'admin@erpcloud.com'
  AND ur.company_id = 4
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = new_role.id AND rp2.permission_id = rp.permission_id
  );

-- 5a) UserRole rows for the NEW user in company 4 (same roles admin
--     already has there).
INSERT INTO user_roles (user_id, role_id, company_id, assigned_at, created_at, updated_at, created_by, updated_by)
SELECT nu.id, ur.role_id, ur.company_id, NOW(), NOW(), NOW(), ur.created_by, ur.updated_by
FROM user_roles ur
JOIN users admin_u ON admin_u.id = ur.user_id
JOIN users nu ON nu.email = 'contador.multiempresa@erpcloud.com'
WHERE admin_u.email = 'admin@erpcloud.com'
  AND ur.company_id = 4
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur2 WHERE ur2.user_id = nu.id AND ur2.role_id = ur.role_id AND ur2.company_id = ur.company_id
  );

-- 5b) UserRole rows for the NEW user in the NEW company (the roles
--     cloned in step 3).
INSERT INTO user_roles (user_id, role_id, company_id, assigned_at, created_at, updated_at, created_by, updated_by)
SELECT nu.id, new_role.id, nc.company_id, NOW(), NOW(), NOW(), nu.created_by, nu.updated_by
FROM roles new_role
JOIN t_companies nc ON nc.tenant_id = 'empresa-piloto-2' AND nc.company_id = new_role.company_id
JOIN users nu ON nu.email = 'contador.multiempresa@erpcloud.com'
WHERE NOT EXISTS (
  SELECT 1 FROM user_roles ur3 WHERE ur3.user_id = nu.id AND ur3.role_id = new_role.id AND ur3.company_id = nc.company_id
);

COMMIT;

-- Verification: run this after and confirm it returns 2 companies for
-- the new user, with the same set of role codes under each.
SELECT c.company_id, c.legal_name, r.code
FROM user_roles ur
JOIN users u ON u.id = ur.user_id
JOIN t_companies c ON c.company_id = ur.company_id
JOIN roles r ON r.id = ur.role_id
WHERE u.email = 'contador.multiempresa@erpcloud.com'
ORDER BY c.company_id, r.code;
