-- 2026-11-01 -- Corrige cities.id: le falta AUTO_INCREMENT.
--
-- Encontrado al correr sql/2026-10-31_populate_cities_municipios.sql:
-- "Error de SQL (1364): Field 'id' doesn't have a default value". La
-- entidad City.java espera @GeneratedValue(strategy = GenerationType.IDENTITY)
-- (i.e. AUTO_INCREMENT), pero la tabla real -- confirmado via
-- SHOW CREATE TABLE cities -- tiene `id BIGINT NOT NULL` sin
-- AUTO_INCREMENT. No es un problema del script de municipios: es un
-- defecto de esquema preexistente que nadie habia notado porque las
-- pocas filas de `cities` que ya existian se insertaron a mano con un
-- id explicito.
--
-- Nota aparte (no bloqueante, no se toca aqui): el UNIQUE real es
-- `uk_cities_country_code` (country_id, code), no solo `code` como
-- sugiere @Column(unique = true) en City.java -- la entidad Java esta
-- desactualizada respecto a la restriccion real, pero no afecta a este
-- fix ni al script de municipios (todas sus filas comparten el mismo
-- country_id = Colombia).
--
-- Seguro de correr con filas ya existentes: al agregar AUTO_INCREMENT a
-- una columna que ya es PRIMARY KEY, MySQL arranca el contador en
-- MAX(id)+1 automaticamente -- no hay riesgo de colisionar con los ids
-- ya usados por companias existentes.

-- UPDATE (mismo dia): el ALTER directo da Error 1833 --
-- "Cannot change column 'id': used in a foreign key constraint
-- 'FKnuljscaqqfl018qb96k1b3016' of table 'erpdb.third_parties'".
-- Limitacion conocida de MySQL/InnoDB: no deja tocar una columna que es
-- blanco de una foreign key de otra tabla (third_parties.city_id ->
-- cities.id), aunque el cambio (agregar AUTO_INCREMENT) no modifica
-- ningun dato ni afecta la relacion en si. Se desactiva la verificacion
-- de FK solo para este ALTER puntual -- no borra ni debilita la
-- constraint, que sigue existiendo y aplicandose normalmente apenas se
-- reactiva en la siguiente linea. No hay cambios de datos entre el
-- SET 0 y el SET 1, asi que no hay ventana real de riesgo.
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE cities MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
SET FOREIGN_KEY_CHECKS = 1;

-- Verificacion: la columna Extra debe decir "auto_increment".
SHOW COLUMNS FROM cities WHERE Field = 'id';
