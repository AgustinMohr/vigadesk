#!/usr/bin/env bash
# Prueba de humo de la observabilidad con el sistema levantado (R1.5):
#  - los cuatro targets de Prometheus (gateway y tres microservicios) estan UP;
#  - Actuator no es alcanzable por las rutas publicas (gateway/HAProxy);
#  - hay metricas HTTP de los servicios.
# Uso: bash ops/smoke-observabilidad.sh [prometheus_url] [base_url]
set -euo pipefail

PROMETHEUS="${1:-http://localhost:9090}"
BASE="${2:-http://localhost}"
JOBS=(gateway ms-clientes ms-pedidos ms-productos)
fallos=0

# Salud de un target: el JSON se parte en una linea por target (separando antes de cada "discoveredLabels")
# y se lee el "health" de la linea que tiene ese job.
salud_de() {
  echo "$1" | sed 's/{"discoveredLabels"/\n&/g' |
    grep "\"job\":\"$2\"" | head -1 | grep -o '"health":"[a-z]*"' | cut -d'"' -f4
}

for intento in $(seq 1 30); do
  targets=$(curl -fsS "$PROMETHEUS/api/v1/targets" | tr -d '\n')
  pendientes=0
  for job in "${JOBS[@]}"; do
    [ "$(salud_de "$targets" "$job")" = "up" ] || pendientes=$((pendientes + 1))
  done
  [ "$pendientes" -eq 0 ] && break
  sleep 5
done

for job in "${JOBS[@]}"; do
  salud=$(salud_de "$targets" "$job")
  echo "target $job: ${salud:-ausente}"
  [ "$salud" = "up" ] || fallos=$((fallos + 1))
done

for ruta in /clientes/actuator/prometheus /productos/actuator/health /pedidos/actuator/prometheus /actuator/prometheus; do
  codigo=$(curl -s -o /dev/null -w '%{http_code}' "$BASE$ruta")
  echo "GET $ruta sin sesion: $codigo"
  case "$codigo" in 401|403|404) ;; *) fallos=$((fallos + 1));; esac
done

series=$(curl -fsS "$PROMETHEUS/api/v1/query?query=http_server_requests_seconds_count" | tr -d '\n' | grep -o '"metric"' | wc -l)
echo "series http_server_requests_seconds_count: $series"
[ "$series" -gt 0 ] || fallos=$((fallos + 1))

if [ "$fallos" -eq 0 ]; then
  echo "OK"
else
  echo "FALLO: $fallos comprobaciones"
  exit 1
fi
