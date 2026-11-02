#!/usr/bin/env bash
# Genera el baseline de Flyway (V1__baseline.sql) desde la base local.
# Credenciales: archivo de opciones MySQL (nunca en la linea de comandos).
#   cp scripts/backup/backup.cnf.example ~/.erp-backup.cnf  y editarlo
# Uso: ./scripts/db/export_baseline.sh [ruta_cnf] [base]
set -euo pipefail
CNF="${1:-$HOME/.erp-backup.cnf}"
DB="${2:-erpdb}"
OUT="src/main/resources/db/migration/V1__baseline.sql"
CATALOGS="countries cities chart_account_templates permissions"

if [ -e "$OUT" ]; then
  echo "Ya existe $OUT. El baseline no se regenera: los cambios nuevos van en V2__*.sql" >&2
  exit 1
fi

{
  echo "-- Flyway baseline generado el $(date -u +%Y-%m-%dT%H:%M:%SZ) desde $DB"
  echo "SET FOREIGN_KEY_CHECKS = 0;"
  mysqldump --defaults-extra-file="$CNF" --no-data --routines --triggers --skip-comments \
    --skip-add-drop-table --set-gtid-purged=OFF --default-character-set=utf8mb4 "$DB" \
    | sed -E 's/ AUTO_INCREMENT=[0-9]+//; s/DEFINER=`[^`]+`@`[^`]+`//g'
  mysqldump --defaults-extra-file="$CNF" --no-create-info --skip-triggers --skip-comments \
    --complete-insert --set-gtid-purged=OFF --default-character-set=utf8mb4 "$DB" $CATALOGS
  echo "SET FOREIGN_KEY_CHECKS = 1;"
} > "$OUT"

echo "OK -> $OUT ($(wc -l < "$OUT") lineas). Revisalo y haz commit."
