#!/usr/bin/env bash
# =============================================================================
# BACKUP LAYER 4 -- monthly restore drill (mandatory).
#
# Restores the production database into a TEMPORARY instance, proves the copy
# is usable by starting the real backend against it (job erp-restore-check:
# Hibernate validates the schema), measures the recovery time and deletes the
# temporary instance. Production is never touched.
#
#   ./scripts/backup/restore_drill.sh pitr     # clone at a point in time (layer 1)
#   ./scripts/backup/restore_drill.sh export   # import the latest daily export (layer 3)
#
# Run it from Cloud Shell (gcloud already authenticated) with:
#   export PROJECT=<project_id> SOURCE_INSTANCE=erp-piloto-mysql BUCKET=<sql_exports_bucket>
# Alternate the two modes month to month. Cost: a few cents (the temp instance
# lives ~15-30 min).
# =============================================================================
set -euo pipefail

MODE="${1:-pitr}"
PROJECT="${PROJECT:?set PROJECT}"
SOURCE_INSTANCE="${SOURCE_INSTANCE:?set SOURCE_INSTANCE}"
REGION="${REGION:-us-east1}"
NETWORK="${NETWORK:-erp-vpc}"
DB_VERSION="${DB_VERSION:-MYSQL_8_0}"
TEMP="erp-drill-$(date -u +%Y%m%d%H%M)"
GC="gcloud --project=${PROJECT} --quiet"
START=$(date +%s)

cleanup() {
  echo ">> Deleting temporary instance ${TEMP}"
  $GC sql instances patch "${TEMP}" --no-deletion-protection >/dev/null 2>&1 || true
  $GC sql instances delete "${TEMP}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

case "${MODE}" in
  pitr)
    POINT=$(date -u -d '-15 minutes' +%Y-%m-%dT%H:%M:%SZ)
    echo ">> Cloning ${SOURCE_INSTANCE} as it was at ${POINT} into ${TEMP}"
    $GC sql instances clone "${SOURCE_INSTANCE}" "${TEMP}" --point-in-time="${POINT}"
    ;;
  export)
    BUCKET="${BUCKET:?set BUCKET}"
    LATEST=$($GC storage ls "gs://${BUCKET}/daily/" | sort | tail -n 1)
    [ -n "${LATEST}" ] || { echo "No exports found in gs://${BUCKET}/daily/"; exit 1; }
    echo ">> Creating ${TEMP} and importing ${LATEST}"
    $GC sql instances create "${TEMP}" --region="${REGION}" --database-version="${DB_VERSION}" \
      --edition=ENTERPRISE --tier=db-f1-micro --network="${NETWORK}" --no-assign-ip
    $GC sql databases create erpdb --instance="${TEMP}" --charset=utf8mb4 --collation=utf8mb4_0900_ai_ci
    APP_PASSWORD=$($GC secrets versions access latest --secret=DB_PASSWORD)
    $GC sql users create erp_app --instance="${TEMP}" --host=% --password="${APP_PASSWORD}"
    TEMP_SA=$($GC sql instances describe "${TEMP}" --format='value(serviceAccountEmailAddress)')
    $GC storage buckets add-iam-policy-binding "gs://${BUCKET}" \
      --member="serviceAccount:${TEMP_SA}" --role=roles/storage.objectViewer >/dev/null
    $GC sql import sql "${TEMP}" "${LATEST}" --database=erpdb
    $GC storage buckets remove-iam-policy-binding "gs://${BUCKET}" \
      --member="serviceAccount:${TEMP_SA}" --role=roles/storage.objectViewer >/dev/null || true
    ;;
  *)
    echo "Usage: $0 pitr|export"; exit 2 ;;
esac

IP=$($GC sql instances describe "${TEMP}" --format='value(ipAddresses[0].ipAddress)')
RESTORED=$(date +%s)
echo ">> Restored in $(( (RESTORED - START) / 60 )) min. Validating with the backend against ${IP}"

$GC run jobs execute erp-restore-check --region="${REGION}" --wait \
  --update-env-vars="DB_URL=jdbc:mysql://${IP}:3306/erpdb?sslMode=REQUIRED&serverTimezone=UTC"

END=$(date +%s)
echo "=================================================================="
echo " RESTORE DRILL OK (${MODE})"
echo " Recovery time (restore + validation): $(( (END - START) / 60 )) min"
echo " Record date, mode and time in the project notes."
echo "=================================================================="
