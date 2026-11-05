# Migraciones de esquema (Flyway)

Auditoría pre-producción 2026-11-02, hallazgo A3. **Activo desde 2026-11-05**:
`V1__baseline.sql` generado desde `erpdb` (23 tablas + catálogos countries,
cities, chart_account_templates, permissions), probado en una base vacía y la
base local `erpdb` ya quedó registrada como versión 1.

## 1. Generar el baseline V1 (una sola vez)

Requisito: haber aplicado antes todos los scripts de `sql/`, incluido
`sql/2026-11-02_tenant_integrity_and_indexes.sql`.

```bash
./scripts/db/export_baseline.sh            # Linux, macOS o Git Bash en Windows
```

Genera `src/main/resources/db/migration/V1__baseline.sql` con:

- el esquema completo de `erpdb` (sin datos de negocio),
- los catálogos que toda instalación nueva necesita: `countries`, `cities`,
  `chart_account_templates`, `permissions`.

Revísalo, haz commit y desde ese momento ese archivo **nunca se edita**.

## 2. Cambios de esquema futuros

Cada cambio nuevo es un archivo `V2__descripcion.sql`, `V3__...` en esta
carpeta (reemplaza la convención manual de `sql/AAAA-MM-DD_*.sql`).

## 3. Cómo se comporta en cada base

| Base | Qué hace Flyway al arrancar con `FLYWAY_ENABLED=true` |
| --- | --- |
| Nueva y vacía (nube) | Ejecuta `V1` y luego `V2+` |
| Restaurada desde un dump de la base local | Ya tiene tablas: registra el baseline en versión 1 y solo ejecuta `V2+` |
| Base local de desarrollo | Igual que la anterior la primera vez; luego aplica los `V2+` nuevos |

En producción (`SPRING_PROFILES_ACTIVE=prod`) Flyway está encendido por defecto.
