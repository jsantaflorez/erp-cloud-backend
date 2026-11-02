#!/usr/bin/env bash
# Restaura un respaldo en una base de PRUEBA y compara conteos (nunca sobre erpdb).
# Uso: ./restore_test.sh /var/backups/erp/erpdb_YYYYMMDD_HHMMSS.sql.gz
# Requiere un usuario con permiso CREATE/DROP sobre erpdb_restore_test en el .cnf
set -euo pipefail
FILE="$1"
CNF="${RESTORE_CNF:-/etc/erp/restore.cnf}"
TEST_DB="${TEST_DB:-erpdb_restore_test}"

[ -f "$FILE.sha256" ] && sha256sum -c "$FILE.sha256"
mysql --defaults-extra-file="$CNF" -e "DROP DATABASE IF EXISTS \`$TEST_DB\`; CREATE DATABASE \`$TEST_DB\` CHARACTER SET utf8mb4;"
gunzip -c "$FILE" | mysql --defaults-extra-file="$CNF" "$TEST_DB"

mysql --defaults-extra-file="$CNF" -t -e "
SELECT 't_companies' tabla, COUNT(*) filas FROM \`$TEST_DB\`.t_companies
UNION ALL SELECT 'chart_of_accounts', COUNT(*) FROM \`$TEST_DB\`.chart_of_accounts
UNION ALL SELECT 'third_parties', COUNT(*) FROM \`$TEST_DB\`.third_parties
UNION ALL SELECT 'journal_entries', COUNT(*) FROM \`$TEST_DB\`.journal_entries
UNION ALL SELECT 'journal_entry_items', COUNT(*) FROM \`$TEST_DB\`.journal_entry_items;"
echo "Restauracion OK en $TEST_DB. Compara los conteos con produccion y luego: DROP DATABASE $TEST_DB;"
