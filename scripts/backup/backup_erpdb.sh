#!/usr/bin/env bash
# Respaldo comprimido y verificado de la base compartida (auditoria 2026-11-02, A6).
# Ejecutar a diario por cron y SIEMPRE antes de un despliegue o actualizacion mayor.
#   15 2 * * * /opt/erp/scripts/backup/backup_erpdb.sh >> /var/log/erp_backup.log 2>&1
# Variables opcionales: BACKUP_CNF, BACKUP_DIR, BACKUP_DB, KEEP_DAYS, BACKUP_UPLOAD_CMD
set -euo pipefail
CNF="${BACKUP_CNF:-/etc/erp/backup.cnf}"
DIR="${BACKUP_DIR:-/var/backups/erp}"
DB="${BACKUP_DB:-erpdb}"
KEEP_DAYS="${KEEP_DAYS:-14}"
FILE="$DIR/${DB}_$(date +%Y%m%d_%H%M%S).sql.gz"

umask 077
mkdir -p "$DIR"

# --single-transaction: snapshot consistente de InnoDB sin bloquear a los usuarios.
mysqldump --defaults-extra-file="$CNF" \
  --single-transaction --quick --routines --triggers --events \
  --hex-blob --set-gtid-purged=OFF --default-character-set=utf8mb4 \
  "$DB" | gzip -9 > "$FILE"

gzip -t "$FILE"                                   # integridad del archivo
zcat "$FILE" | tail -n 1 | grep -q "Dump completed" \
  || { echo "ERROR: dump incompleto en $FILE" >&2; exit 1; }
sha256sum "$FILE" > "$FILE.sha256"

# Copia fuera del servidor (bucket con cifrado). Ejemplos:
#   BACKUP_UPLOAD_CMD='aws s3 cp'   o   BACKUP_UPLOAD_CMD='gsutil cp'
if [ -n "${BACKUP_UPLOAD_CMD:-}" ] && [ -n "${BACKUP_UPLOAD_DEST:-}" ]; then
  $BACKUP_UPLOAD_CMD "$FILE" "$BACKUP_UPLOAD_DEST/"
  $BACKUP_UPLOAD_CMD "$FILE.sha256" "$BACKUP_UPLOAD_DEST/"
fi

find "$DIR" -name "${DB}_*.sql.gz*" -mtime +"$KEEP_DAYS" -delete
echo "$(date -Is) OK $FILE ($(du -h "$FILE" | cut -f1))"
