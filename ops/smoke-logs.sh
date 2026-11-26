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

# El identificador enviado al gateway tiene que verse en los logs del gateway y del servicio, y volver
base=${GATEWAY_URL:-http://127.0.0.1:3080}
id="humo-$$-$RANDOM"
cabeceras=$(curl -s -D - -o /dev/null -H "X-Request-Id: $id" -X POST "$base/api/usuarios/login"   -H 'Content-Type: application/json' -d '{"email":"humo@example.com","password":"no-existe"}')
[ "$(grep -ci "^x-request-id: $id" <<<"$cabeceras")" -eq 1 ] || fallo "la respuesta no devuelve el X-Request-Id una vez"
sleep 2
for c in ms-gateway ms-clientes; do
  if docker logs "$c" 2>&1 | grep -q "\"requestId\":\"$id\""; then
    echo "OK: $c registra el identificador"
  else
    fallo "$c: no registra el identificador $id"
  fi
done

if [ "$fallos" -gt 0 ]; then
  echo "$fallos comprobaciones fallidas"
  exit 1
fi
echo "Logs correctos"
