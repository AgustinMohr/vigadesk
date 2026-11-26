#!/usr/bin/env bash
# Prueba de humo de los logs con el stack levantado: cada servicio Java escribe JSON a stdout
# (incluida la linea de arranque) y SLF4J tiene un proveedor. Sale con 1 si algo falla.
set -u

fallos=0
fallo() { echo "FALLA: $*"; fallos=$((fallos + 1)); }

for c in ms-eureka ms-clientes ms-productos ms-pedidos ms-gateway; do
  logs=$(docker logs "$c" 2>&1)

  if grep -q 'No SLF4J providers' <<<"$logs"; then
    fallo "$c: sin implementacion de logs (SLF4J en modo NOP)"
  fi
  if ! grep -q '^{.*"message":"Started [A-Za-z]*Application' <<<"$logs"; then
    fallo "$c: no hay linea de arranque en JSON"
  fi
  # Fuera de JSON solo se admiten los avisos que la JVM y spring-jcl escriben antes de configurar los logs
  sueltas=$(grep -v -e '^{' -e '^Picked up JAVA_TOOL_OPTIONS' -e '^Standard Commons Logging discovery' <<<"$logs")
  if [ -n "$sueltas" ]; then
    fallo "$c: lineas que no son JSON:"
    head -5 <<<"$sueltas"
  fi
  if grep -q '"level":"DEBUG"' <<<"$logs"; then
    fallo "$c: hay lineas DEBUG con el nivel por defecto"
  fi
  [ "$fallos" -eq 0 ] && echo "OK: $c"
done

if [ "$fallos" -gt 0 ]; then
  echo "$fallos comprobaciones fallidas"
  exit 1
fi
echo "Logs correctos"
