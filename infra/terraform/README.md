# Infraestructura del piloto en Google Cloud (Terraform)

Implementa la arquitectura de `arquitectura-cloud-piloto-2026-11-05` y la
estrategia de backup del Claude Doc "Estrategia de backup y recuperación —
ERP Cloud". Validada con OpenTofu 1.8.5 + proveedor `hashicorp/google` 8.10
(`validate` y `fmt` limpios). **Todavía no se ha aplicado** en ningún proyecto.

| Archivo | Qué crea |
| --- | --- |
| `apis.tf` | APIs del proyecto |
| `network.tf` | VPC privada, subred para Cloud Run, peering para Cloud SQL |
| `sql.tf` | Cloud SQL MySQL (solo IP privada, SSL obligatorio), base `erpdb`, usuarios `erp_app` y `erp_migrate` — **backup capa 1** |
| `secrets.tf` | `DB_PASSWORD`, `DB_MIGRATE_PASSWORD`, `JWT_SECRET` generados y guardados en Secret Manager |
| `run.tf` | Servicio `erp-backend` (0–1 instancias), job `erp-db-migrate` (Flyway), job `erp-restore-check` (capa 4) |
| `registry.tf` | Artifact Registry `erp` (conserva las 2 últimas imágenes) |
| `iam.tf`, `ci.tf` | Cuentas de servicio con permisos mínimos y acceso de GitHub Actions sin llaves (Workload Identity) |
| `backup.tf` | Bucket de exports, workflow y scheduler diario (**capa 3**) y alertas por correo |
| `budget.tf` | Presupuesto de 15 USD que excluye créditos, alertas 50/90/100 % y 100 % proyectado |

## Estrategia de backup implementada

| Capa | Dónde | Qué hace |
| --- | --- | --- |
| 1. Backups automáticos + PITR | `sql.tf` | Backup diario 02:00 Bogotá, 14 copias, recuperación a cualquier minuto de los últimos 7 días. Los backups sobreviven si se borra la instancia y se toma un backup final antes de cualquier borrado. Doble protección contra borrado (Terraform y la API). |
| 2. Backup antes de cada despliegue | `.github/workflows/deploy-backend.yml` + rol `erpPreDeployBackup` | `gcloud sql backups create` justo antes de ejecutar las migraciones de Flyway. |
| 3. Export diario | `backup.tf` | 03:00 Bogotá (después del backup automático; Cloud SQL no permite dos operaciones a la vez) export `.sql.gz` a un bucket multirregión `US`, sin acceso público, que no deja borrar ni sobrescribir durante 30 días. |
| 4. Simulacro mensual | `run.tf` + `scripts/backup/restore_drill.sh` | Restaura en una instancia temporal, arranca el backend real contra la copia (Hibernate valida el esquema), mide el tiempo y borra la instancia. |
| Alertas | `backup.tf` | Correo si falla el export, si el scheduler no lo lanza o si falla una operación de Cloud SQL (incluye backups). |

## Puesta en marcha (una sola vez)

### 1. Proyecto y bucket de estado
```bash
gcloud projects create <PROJECT_ID>            # o usa uno existente
gcloud billing projects link <PROJECT_ID> --billing-account=<BILLING_ACCOUNT>
gcloud config set project <PROJECT_ID>
gcloud storage buckets create gs://<PROJECT_ID>-tfstate --location=us-east1 \
  --uniform-bucket-level-access --public-access-prevention
gcloud storage buckets update gs://<PROJECT_ID>-tfstate --versioning
```
El estado de Terraform contiene las contraseñas generadas: ese bucket debe seguir privado.

### 2. Variables
```bash
cp envs/piloto.tfvars.example envs/piloto.tfvars   # git lo ignora
```
Completa `project_id`, `billing_account`, `alert_email`, `region`, `cors_allowed_origins` y `db_version`.
La versión debe coincidir con la local (`docker exec mysql_erp mysql --version`): 8.0 → `MYSQL_8_0`, 8.4 → `MYSQL_8_4`.

### 3. Aplicar
```bash
cd infra/terraform
terraform init -backend-config="bucket=<PROJECT_ID>-tfstate" -backend-config="prefix=erp-cloud/piloto"
terraform plan  -var-file=envs/piloto.tfvars
terraform apply -var-file=envs/piloto.tfvars
```
La primera vez el servicio usa una imagen de ejemplo; el primer despliegue (paso 5) pone la real.
Haz commit del `.terraform.lock.hcl` que genera `terraform init`.

### 4. Permisos de los usuarios de la base
Cloud SQL crea los usuarios con privilegios amplios. Déjalos en lo mínimo desde **Cloud SQL Studio** (consola → la instancia → Cloud SQL Studio, usuario `root` o uno con privilegios):
```sql
REVOKE cloudsqlsuperuser FROM 'erp_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON erpdb.* TO 'erp_app'@'%';

REVOKE cloudsqlsuperuser FROM 'erp_migrate'@'%';
GRANT ALL PRIVILEGES ON erpdb.* TO 'erp_migrate'@'%';
```
Si Studio no logra conectar por ser una instancia solo con IP privada, habilita temporalmente el acceso por Data API en la configuración de la instancia y vuelve a deshabilitarlo después.

### 5. GitHub Actions
En `erp-cloud-backend` → Settings → Secrets and variables → Actions → **Variables**, crea con los valores de `terraform output`:
`GCP_PROJECT_ID`, `GCP_REGION`, `WIF_PROVIDER`, `CI_SERVICE_ACCOUNT`, `AR_REPOSITORY`, `SQL_INSTANCE`.
Crea también el environment `piloto` (Settings → Environments); ahí puedes exigir aprobación manual.
Despliega desde Actions → "Deploy backend (piloto)" → Run workflow. En la primera ejecución Flyway crea todo el esquema desde `V1__baseline.sql`.

Verifica que el job `erp-db-migrate` termina solo (estado *Succeeded*). Si queda corriendo hasta el timeout, la aplicación tiene algún hilo que no deja terminar al proceso sin servidor web y hay que agregar una salida explícita.

### 6. Primer administrador y compañía — PENDIENTE
`V1` no trae datos de negocio. Falta definir cómo se crean el primer usuario `SYS_ADMIN`, la compañía y sus roles en una base nueva.

## Operación del backup

- **Día siguiente al primer apply:** confirma que hay un archivo en `gs://<bucket>/daily/` y que llegó el correo de verificación del canal de alertas (hay que aceptarlo).
- **Al cumplir un mes sin problemas:** bloquea la retención del bucket. Es irreversible: después nadie puede borrar exports de menos de 30 días.
  `gcloud storage buckets update gs://<bucket> --lock-retention-period`
- **Cada mes:** simulacro, alternando modos, desde Cloud Shell:
  ```bash
  export PROJECT=<PROJECT_ID> SOURCE_INSTANCE=erp-piloto-mysql BUCKET=<bucket>
  ./scripts/backup/restore_drill.sh pitr     # mes impar
  ./scripts/backup/restore_drill.sh export   # mes par
  ```
  Anota fecha, modo y tiempo de recuperación en las notas del proyecto.
- **Restaurar de verdad** (incidente): clonar a un punto en el tiempo con `gcloud sql instances clone ... --point-in-time=...`, validar con `erp-restore-check` y apuntar el servicio a la copia, o restaurar un backup sobre la instancia (`gcloud sql backups restore`). Nunca restaurar sobre producción sin un backup a demanda previo.

## Decisiones pendientes que afectan estas variables
1. Región: `us-east1` (por defecto) o `northamerica-south1`.
2. Dominio del piloto → `cors_allowed_origins`.
3. Rama de producción: por ahora el despliegue es manual (`workflow_dispatch`).
4. Ping programado para evitar arranques en frío (no incluido).

Pendiente aparte: el frontend en Firebase Hosting (`firebase.json`, rewrite `/api/**` → `erp-backend`) no está en esta configuración.
