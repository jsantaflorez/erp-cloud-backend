-- 2026-10-29 -- Follow-up to 2026-10-28_seed_multiempresa_test_user.sql:
-- adds company_id = 5 (already existed before this testing round) as a
-- THIRD company accessible to contador.multiempresa@erpcloud.com, so the
-- AppHeader switcher can be tested across 3 real companies instead of
-- just the synthetic "Piloto 2" one.
--
-- Unlike the previous script, company 5 is NOT created here -- it
-- already exists and may already have its own roles/users configured.
-- This script is deliberately conservative:
--   * reuses a role in company 5 if one with the same CODE as one of
--     admin@erpcloud.com's company-4 roles already exists there --
--     never creates a duplicate;
--   * only clones permissions onto a role this script itself just
--     created (a role that currently has ZERO permissions rows) --
--     it never adds permissions to a role that already existed in
--     company 5, so nothing pre-configured there gets silently changed;
--   * only touches user_roles for contador.multiempresa@erpcloud.com --
--     no other user's access in company 5 is read or written.
--
-- Safe to re-run (every INSERT is NOT EXISTS-guarded).

START TRANSACTION;

-- 1) Clone admin@erpcloud.com's company-4 role codes onto company 5,
--    but only the ones company 5 doesn't already have.
INSERT INTO roles (company_id, name, code, system_role)
SELECT 5, r.name, r.code, r.system_role
FROM roles r
JOIN user_roles ur ON ur.role_id = r.id
JOIN users u ON u.id = ur.user_id
WHERE u.email = 'admin@erpcloud.com'
  AND ur.company_id = 4
  AND NOT EXISTS (
    SELECT 1 FROM roles r2 WHERE r2.company_id = 5 AND r2.code = r.code
  );

-- 2) Permissions for each matching role in company 5, ONLY if that
--    role currently has none at all (i.e. it's either the one just
--    created above, or a pre-existing-but-never-configured role --
--    either way, safe to fill in; never touches an already-configured
--    role's permission set).
INSERT INTO role_permissions (role_id, permission_id)
SELECT new_role.id, rp.permission_id
FROM role_permissions rp
JOIN roles old_role ON old_role.id = rp.role_id
JOIN user_roles ur ON ur.role_id = old_role.id
JOIN users u ON u.id = ur.user_id
JOIN roles new_role ON new_role.company_id = 5 AND new_role.code = old_role.code
WHERE u.email = 'admin@erpcloud.com'
  AND ur.company_id = 4
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = new_role.id)
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp3 WHERE rp3.role_id = new_role.id AND rp3.permission_id = rp.permission_id
  );

-- 3) Give the test user UserRole rows in company 5, one per matching
--    role code (whichever existed already or was just created above).
INSERT INTO user_roles (user_id, role_id, company_id, assigned_at, created_at, updated_at, created_by, updated_by)
SELECT nu.id, company5_role.id, 5, NOW(), NOW(), NOW(), nu.created_by, nu.updated_by
FROM roles company5_role
JOIN user_roles admin_ur ON admin_ur.role_id IN (
    SELECT r.id FROM roles r
    JOIN user_roles ur2 ON ur2.role_id = r.id
    JOIN users au ON au.id = ur2.user_id
    WHERE au.email = 'admin@erpcloud.com' AND ur2.company_id = 4
)
JOIN roles admin_role ON admin_role.id = admin_ur.role_id AND admin_role.code = company5_role.code
JOIN users nu ON nu.email = 'contador.multiempresa@erpcloud.com'
WHERE company5_role.company_id = 5
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur3 WHERE ur3.user_id = nu.id AND ur3.role_id = company5_role.id AND ur3.company_id = 5
  )
GROUP BY company5_role.id, nu.id, nu.created_by, nu.updated_by;

COMMIT;

-- Verification: should now show company 4, company 5, and the
-- "Piloto 2" company (if you still have that one too) for this user.
SELECT c.company_id, c.legal_name, r.code
FROM user_roles ur
JOIN users u ON u.id = ur.user_id
JOIN t_companies c ON c.company_id = ur.company_id
JOIN roles r ON r.id = ur.role_id
WHERE u.email = 'contador.multiempresa@erpcloud.com'
ORDER BY c.company_id, r.code;
