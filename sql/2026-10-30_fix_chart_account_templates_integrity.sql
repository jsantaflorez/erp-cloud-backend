-- ============================================================
-- Fase 4 (Clonacion de PUC): corrige 3 defectos de integridad
-- encontrados en la tabla de referencia chart_account_templates
-- (sql/2026-09-10_chart_account_templates.sql) al construir el
-- clonador real de PUC. Sin estos fixes, el clonador encuentra
-- codigos huerfanos (sin grupo padre) o de longitud invalida y
-- DEBE RECHAZAR la importacion en vez de inventar un grupo falso
-- -- por eso los defectos se corrigen aqui, en el dato fuente, no
-- en tiempo de ejecucion. Ver ChartAccountTemplateImportService.
-- ============================================================

START TRANSACTION;

-- 1) COMERCIAL: falta el grupo '47' (Ajustes por Inflacion) como
--    fila propia -- su hijo '4705' (Correccion monetaria) y los
--    demas hijos de 47xx ya existen, pero el grupo de 2 digitos
--    nunca se sembro. SOLIDARIO si lo tiene ('47', 'AJUSTES POR
--    INFLACION'), lo que confirma que es un hueco real de la
--    siembra de COMERCIAL, no una diferencia intencional entre
--    plantillas.
INSERT INTO chart_account_templates (template_type, code, name)
SELECT 'COMERCIAL', '47', 'Ajustes por inflacion'
WHERE NOT EXISTS (
    SELECT 1 FROM chart_account_templates
    WHERE template_type = 'COMERCIAL' AND code = '47'
);

-- 2) SOLIDARIO: '734730' y '734735' son filas duplicadas y mal
--    codificadas -- comparten nombre EXACTO ('CONSTRUCCIONES Y
--    EDIFICACIONES' / 'MAQUINARIA Y EQUIPO') con '736005'/'736010',
--    que ya existen correctamente bajo el grupo '7360' (DEPRECIA-
--    CIONES, dentro de Costos Indirectos). '7347' no es un grupo
--    real en ningun lado de la plantilla (ni como fila propia ni
--    como padre de nada mas) -- es un defecto de digitacion del
--    CSV original, no un grupo faltante. Se eliminan en vez de
--    reparentarlas, porque la informacion que representan ya esta
--    correctamente presente bajo 7360.
DELETE FROM chart_account_templates
WHERE template_type = 'SOLIDARIO' AND code IN ('734730', '734735');

-- 3) SOLIDARIO: '251002113' (9 digitos -- ningun salto de PUC
--    llega a 9 digitos) es un typo de '25100213': continua
--    exactamente la secuencia real de sus hermanos (25100210,
--    25100211, 25100212), todos bajo el grupo '251002' (IVA
--    Descontable).
UPDATE chart_account_templates
SET code = '25100213'
WHERE template_type = 'SOLIDARIO' AND code = '251002113';

-- Verificacion: no debe haber codigos huerfanos (sin su grupo
-- padre esperado) en ninguna de las dos plantillas, excluyendo
-- las cuentas de orden (clases 8 y 9, fuera del alcance del
-- clonador -- ver ChartAccountTemplateImportService). Esta
-- consulta debe devolver 0 filas tras los fixes de arriba; si
-- devuelve alguna, el clonador la va a rechazar con
-- TEMPLATE_INTEGRITY_GAP -- no correr la importacion hasta
-- resolverla aqui, en la tabla fuente.
SELECT
    t1.template_type,
    t1.code,
    CASE
        WHEN LENGTH(t1.code) = 2 THEN LEFT(t1.code, 1)
        ELSE LEFT(t1.code, LENGTH(t1.code) - 2)
    END AS expected_parent_code
FROM chart_account_templates t1
WHERE t1.template_type IN ('COMERCIAL', 'SOLIDARIO')
  AND LEFT(t1.code, 1) NOT IN ('8', '9')
  AND LENGTH(t1.code) > 2
  AND NOT EXISTS (
      SELECT 1 FROM chart_account_templates t2
      WHERE t2.template_type = t1.template_type
        AND t2.code = CASE
            WHEN LENGTH(t1.code) = 2 THEN LEFT(t1.code, 1)
            ELSE LEFT(t1.code, LENGTH(t1.code) - 2)
        END
  );

COMMIT;
