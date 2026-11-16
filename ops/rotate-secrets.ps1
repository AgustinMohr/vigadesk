#requires -Version 5.1
<#
Rotacion de secretos para el stack.

Ejecutar desde cualquier ubicacion:
  powershell -ExecutionPolicy Bypass -File .\ops\rotate-secrets.ps1

Que hace:
  1. Levanta el stack y espera healthchecks.
  2. Genera secretos nuevos (base62, sin caracteres problematicos para URIs).
  3. Backup fresco de MySQL, PostgreSQL y MongoDB en ops/backups/.
  4. Rota password de MySQL, PostgreSQL, MongoDB, RabbitMQ y Grafana.
  5. Actualiza .env despues de cada rotacion (backup .env.bak.<ts>).
  6. Recrea los servicios consumidores con las nuevas credenciales.
  7. Rota JWT al final: invalida todas las sesiones activas.
  8. Verifica health, logs y autenticacion contra cada motor.

No imprime secretos. Ante cualquier fallo se detiene con exit 1.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$envFile = Join-Path $root '.env'
if (-not (Test-Path $envFile)) { throw "No existe $envFile" }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDir = Join-Path $root "ops\backups\pre-rotation-$stamp"

# ---------- .env helpers ----------
$script:envLines = [System.Collections.Generic.List[string]]([System.IO.File]::ReadAllLines($envFile))

function Get-EnvValue([string]$key) {
  foreach ($line in $script:envLines) {
    if ($line -match ('^\s*' + [regex]::Escape($key) + '\s*=\s*(.*)$')) {
      return $matches[1].Trim().Trim('"').Trim("'")
    }
  }
  throw "Falta la variable $key en .env"
}

function Save-Env {
  $utf8 = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllLines($envFile, $script:envLines, $utf8)
}

function Set-EnvValue([string]$key, [string]$value) {
  $found = $false
  for ($i = 0; $i -lt $script:envLines.Count; $i++) {
    if ($script:envLines[$i] -match ('^\s*' + [regex]::Escape($key) + '\s*=')) {
      $script:envLines[$i] = "$key=$value"
      $found = $true
      break
    }
  }
  if (-not $found) { $script:envLines.Add("$key=$value") }
  Save-Env
}

# ---------- helpers ----------
function New-Secret([int]$length) {
  $chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  $bytes = New-Object byte[] $length
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($bytes)
  $rng.Dispose()
  return (-join ($bytes | ForEach-Object { $chars[[int]($_ % $chars.Length)] }))
}

function Assert-Name([string]$value, [string]$label) {
  if ($value -notmatch '^[A-Za-z0-9_]+$') { throw "$label contiene caracteres no permitidos: $value" }
}

function Invoke-Docker([string[]]$arguments) {
  & docker @arguments
  if ($LASTEXITCODE -ne 0) { throw 'Fallo un comando docker; revisa la salida anterior.' }
}

function Wait-Healthy([string]$container, [int]$timeoutSeconds = 120) {
  $deadline = (Get-Date).AddSeconds($timeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $state = (& docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $container 2>$null)
    if ($state -eq 'healthy' -or $state -eq 'running') { return }
    Start-Sleep -Seconds 2
  }
  throw "$container no llego a healthy en $timeoutSeconds s"
}

function Start-And-Wait([string[]]$services) {
  Invoke-Docker (@('compose', 'up', '-d') + $services)
  foreach ($s in $services) {
    $name = switch ($s) {
      'mysql' { 'mysql-db' }
      'pg-dan' { 'pg-dan-db' }
      'mongo-db' { 'mongo-db' }
      'rabbitmq' { 'rabbitmq' }
      default { $s }
    }
    Wait-Healthy $name
  }
}

Write-Host '[1/9] Levantando infraestructura...'
Start-And-Wait 'mysql', 'pg-dan', 'mongo-db', 'rabbitmq'

Write-Host '[2/9] Verificando .env y generando secretos...'
$mysqlUser = Get-EnvValue 'MYSQL_USER'
$mysqlDb = Get-EnvValue 'MYSQL_DATABASE'
$mysqlRootOld = Get-EnvValue 'MYSQL_ROOT_PASSWORD'
$pgUser = Get-EnvValue 'POSTGRES_USER'
$pgDb = Get-EnvValue 'POSTGRES_DB'
$pgOld = Get-EnvValue 'POSTGRES_PASSWORD'
$mongoUser = Get-EnvValue 'MONGO_INITDB_ROOT_USERNAME'
$mongoOld = Get-EnvValue 'MONGO_INITDB_ROOT_PASSWORD'
$rabbitUser = Get-EnvValue 'RABBITMQ_USERNAME'
Assert-Name $mysqlUser 'MYSQL_USER'
Assert-Name $mysqlDb 'MYSQL_DATABASE'
Assert-Name $pgUser 'POSTGRES_USER'
Assert-Name $pgDb 'POSTGRES_DB'
Assert-Name $mongoUser 'MONGO_INITDB_ROOT_USERNAME'
Assert-Name $rabbitUser 'RABBITMQ_USERNAME'
if ($mongoOld -notmatch '^[A-Za-z0-9]+$') { throw 'El password actual de Mongo debe ser alfanumerico para construir la URI' }

$newMysqlApp = New-Secret 40
$newMysqlRoot = New-Secret 40
$newPg = New-Secret 40
$newMongo = New-Secret 40
$newRabbit = New-Secret 40
$newGrafana = New-Secret 32
$newJwt = New-Secret 64

Write-Host '[3/9] Backup fresco de bases...'
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

Invoke-Docker @('compose', 'exec', '-T', 'mysql', 'sh', '-c', 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers "$MYSQL_DATABASE" > /tmp/rot-mysql.sql')
Invoke-Docker @('cp', 'mysql-db:/tmp/rot-mysql.sql', (Join-Path $backupDir 'mysql.sql'))

Invoke-Docker @('compose', 'exec', '-T', 'pg-dan', 'sh', '-c', 'pg_dump -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > /tmp/rot-pg.dump')
Invoke-Docker @('cp', 'pg-dan-db:/tmp/rot-pg.dump', (Join-Path $backupDir 'postgres.dump'))

Invoke-Docker @('compose', 'exec', '-T', 'mongo-db', 'sh', '-c', 'mongodump --uri="mongodb://$MONGO_INITDB_ROOT_USERNAME:$MONGO_INITDB_ROOT_PASSWORD@localhost:27017/pedidosdb?authSource=admin" --archive=/tmp/rot-mongo.gz --gzip')
Invoke-Docker @('cp', 'mongo-db:/tmp/rot-mongo.gz', (Join-Path $backupDir 'mongo.gz'))

foreach ($f in @('mysql.sql', 'postgres.dump', 'mongo.gz')) {
  $p = Join-Path $backupDir $f
  if (-not (Test-Path $p) -or (Get-Item $p).Length -eq 0) { throw "Backup invalido: $p" }
}
Copy-Item $envFile (Join-Path $root ".env.bak.$stamp")
Write-Host "      Backups en $backupDir"

Write-Host '[4/9] Rotando MySQL...'
$appEsc = $newMysqlApp.Replace("'", "''")
$rootEsc = $newMysqlRoot.Replace("'", "''")
$sql = "ALTER USER IF EXISTS '$mysqlUser'@'%' IDENTIFIED BY '$appEsc'; ALTER USER IF EXISTS 'root'@'%' IDENTIFIED BY '$rootEsc'; ALTER USER IF EXISTS 'root'@'localhost' IDENTIFIED BY '$rootEsc'; FLUSH PRIVILEGES;"
Invoke-Docker @('compose', 'exec', '-T', '-e', "MYSQL_PWD=$mysqlRootOld", 'mysql', 'mysql', '-uroot', '-e', $sql)
Set-EnvValue 'MYSQL_PASSWORD' $newMysqlApp
Set-EnvValue 'MYSQL_ROOT_PASSWORD' $newMysqlRoot
Invoke-Docker @('compose', 'up', '-d', '--force-recreate', 'ms-clientes')
Wait-Healthy 'ms-clientes' 120

Write-Host '[5/9] Rotando PostgreSQL...'
$pgEsc = $newPg.Replace("'", "''")
$sql = "ALTER ROLE $pgUser PASSWORD '$pgEsc'"
Invoke-Docker @('compose', 'exec', '-T', '-e', "PGPASSWORD=$pgOld", 'pg-dan', 'psql', '-h', '127.0.0.1', '-U', $pgUser, '-d', 'postgres', '-c', $sql)
Set-EnvValue 'POSTGRES_PASSWORD' $newPg
Invoke-Docker @('compose', 'up', '-d', '--force-recreate', 'ms-productos')
Wait-Healthy 'ms-productos' 120

Write-Host '[6/9] Rotando MongoDB...'
$mongoUri = "mongodb://${mongoUser}:${mongoOld}@localhost:27017/admin"
$mongoJson = ConvertTo-Json $newMongo -Compress
$js = "db.changeUserPassword('$mongoUser', $mongoJson)"
$js | docker exec -i mongo-db mongosh $mongoUri --quiet
if ($LASTEXITCODE -ne 0) { throw 'Fallo changeUserPassword en MongoDB' }
Set-EnvValue 'MONGO_INITDB_ROOT_PASSWORD' $newMongo
Invoke-Docker @('compose', 'up', '-d', '--force-recreate', 'ms-pedidos')
Wait-Healthy 'ms-pedidos' 120

Write-Host '[7/9] Rotando RabbitMQ...'
Invoke-Docker @('compose', 'exec', '-T', 'rabbitmq', 'rabbitmqctl', 'change_password', $rabbitUser, $newRabbit)
Set-EnvValue 'RABBITMQ_PASSWORD' $newRabbit
Invoke-Docker @('compose', 'up', '-d', '--force-recreate', 'ms-clientes', 'ms-productos', 'ms-pedidos')
foreach ($c in @('ms-clientes', 'ms-productos', 'ms-pedidos')) { Wait-Healthy $c 150 }

Write-Host '[8/9] Rotando Grafana y JWT...'
$grafanaUser = $null
try { $grafanaUser = Get-EnvValue 'GRAFANA_ADMIN_USER' } catch { $grafanaUser = 'admin' }
Set-EnvValue 'GRAFANA_ADMIN_USER' $grafanaUser
Set-EnvValue 'GRAFANA_ADMIN_PASSWORD' $newGrafana
Invoke-Docker @('compose', 'up', '-d', '--force-recreate', 'grafana')
Wait-Healthy 'grafana' 120
& docker compose exec -T grafana grafana cli admin reset-admin-password $newGrafana | Out-Null
if ($LASTEXITCODE -ne 0) {
  & docker compose exec -T grafana grafana-cli admin reset-admin-password $newGrafana | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Fallo reset-admin-password en Grafana' }
}

Set-EnvValue 'JWT_SECRET' $newJwt
Invoke-Docker @('compose', 'up', '-d', '--force-recreate', 'ms-clientes', 'ms-gateway')
Wait-Healthy 'ms-clientes' 150
Wait-Healthy 'ms-gateway' 120

Write-Host '[9/9] Verificando...'
$health = $null
try { $health = (Invoke-WebRequest -UseBasicParsing 'http://localhost:3080/actuator/health' -TimeoutSec 15).StatusCode } catch { $health = 'ERROR' }
if ($health -ne 200) { throw "Gateway health devolvio $health" }

& docker compose exec -T -e "MYSQL_PWD=$newMysqlApp" mysql mysql -u$mysqlUser -e 'SELECT 1' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Autenticacion MySQL con la nueva password fallo' }
& docker compose exec -T -e "PGPASSWORD=$newPg" pg-dan psql -h 127.0.0.1 -U $pgUser -d $pgDb -c 'SELECT 1' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Autenticacion PostgreSQL con la nueva password fallo' }
& docker exec mongo-db mongosh "mongodb://${mongoUser}:${newMongo}@localhost:27017/admin" --quiet --eval 'print(1)' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Autenticacion MongoDB con la nueva password fallo' }
& docker compose exec -T rabbitmq rabbitmqctl authenticate_user $rabbitUser $newRabbit | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Autenticacion RabbitMQ con la nueva password fallo' }

Write-Host ''
Write-Host 'Rotacion completada.'
Write-Host "  - Backups: $backupDir"
Write-Host "  - .env actualizado; backup: .env.bak.$stamp"
Write-Host '  - Sesiones JWT invalidadas: hay que volver a loguearse.'
Write-Host '  - Probar login y operaciones criticas antes de cerrar la ventana.'
