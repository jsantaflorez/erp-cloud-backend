-- 2026-11-01 -- Fixes cities.id: it is missing AUTO_INCREMENT.
--
-- Found while running sql/2026-10-31_populate_cities_municipios.sql:
-- "SQL Error (1364): Field 'id' doesn't have a default value". The
-- City.java entity expects @GeneratedValue(strategy = GenerationType.IDENTITY)
-- (i.e. AUTO_INCREMENT), but the real table -- confirmed via
-- SHOW CREATE TABLE cities -- has `id BIGINT NOT NULL` with no
-- AUTO_INCREMENT. Not a problem with the municipios script itself: it is
-- a pre-existing schema defect nobody had noticed because the few rows
-- `cities` already had were inserted by hand with an explicit id.
--
-- Side note (non-blocking, not touched here): the real UNIQUE constraint
-- is `uk_cities_country_code` (country_id, code), not just `code` as
-- @Column(unique = true) on City.java suggests -- the Java entity is out
-- of sync with the real constraint, but that does not affect this fix or
-- the municipios script (all its rows share the same country_id =
-- Colombia).
--
-- Safe to run with existing rows: when AUTO_INCREMENT is added to a
-- column that is already the PRIMARY KEY, MySQL starts the counter at
-- MAX(id)+1 automatically -- no risk of colliding with ids already used
-- by existing companies.

-- UPDATE (same day): the direct ALTER fails with Error 1833 --
-- "Cannot change column 'id': used in a foreign key constraint
-- 'FKnuljscaqqfl018qb96k1b3016' of table 'erpdb.third_parties'".
-- Known MySQL/InnoDB limitation: it will not let you touch a column that
-- is the target of another table's foreign key (third_parties.city_id ->
-- cities.id), even though the change (adding AUTO_INCREMENT) does not
-- modify any data or affect the relationship itself. FK checks are
-- disabled only for this one ALTER -- the constraint itself is never
-- dropped or weakened, and applies normally again as soon as it is
-- re-enabled on the next line. No data changes happen between the SET 0
-- and the SET 1, so there is no real window of risk.
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE cities MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
SET FOREIGN_KEY_CHECKS = 1;

-- Verification: the Extra column should say "auto_increment".
SHOW COLUMNS FROM cities WHERE Field = 'id';
