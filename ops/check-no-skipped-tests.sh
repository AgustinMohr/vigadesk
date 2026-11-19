#!/usr/bin/env bash
# Falla si algun reporte de Surefire/Failsafe tiene tests omitidos (skipped > 0).
# Uso: ops/check-no-skipped-tests.sh [directorio-raiz]
set -euo pipefail

root="${1:-.}"
status=0
count=0

while IFS= read -r -d '' xml; do
  count=$((count + 1))
  skipped=$(grep -o -m1 'skipped="[0-9]*"' "$xml" | head -1 | grep -o '[0-9]*' || echo 0)
  if [ "${skipped:-0}" -gt 0 ]; then
    echo "Tests omitidos ($skipped) en $xml" >&2
    status=1
  fi
done < <(find "$root" -path '*/target/*-reports/TEST-*.xml' -print0)

if [ "$count" -eq 0 ]; then
  echo "No se encontraron reportes de tests bajo $root" >&2
  exit 1
fi

exit $status
