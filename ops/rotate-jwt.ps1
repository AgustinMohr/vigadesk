#requires -Version 5.1
<#
Rota solo JWT_SECRET y recrea ms-clientes + ms-gateway.

  powershell -ExecutionPolicy Bypass -File .\ops\rotate-jwt.ps1

Efecto: todas las sesiones activas quedan invalidadas.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$envFile = Join-Path $root '.env'
if (-not (Test-Path $envFile)) { throw "No existe $envFile" }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$script:envLines = [System.Collections.Generic.List[string]]([System.IO.File]::ReadAllLines($envFile))

function New-Secret([int]$length) {
  $chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  $bytes = New-Object byte[] $length
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  $rng.GetBytes($bytes)
  $rng.Dispose()
  return (-join ($bytes | ForEach-Object { $chars[[int]($_ % $chars.Length)] }))
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
  $utf8 = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllLines($envFile, $script:envLines, $utf8)
}

function Invoke-Docker([string[]]$arguments) {
  & docker @arguments
  if ($LASTEXITCODE -ne 0) { throw 'Fallo un comando docker; revisa la salida anterior.' }
}

function Wait-Healthy([string]$container, [int]$timeoutSeconds = 150) {
  $deadline = (Get-Date).AddSeconds($timeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $state = (& docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $container 2>$null)
    if ($state -eq 'healthy' -or $state -eq 'running') { return }
    Start-Sleep -Seconds 2
  }
  throw "$container no llego a healthy en $timeoutSeconds s"
}

Write-Host '[1/3] Generando JWT_SECRET y actualizando .env...'
Copy-Item $envFile (Join-Path $root ".env.bak.$stamp")
Set-EnvValue 'JWT_SECRET' (New-Secret 64)
Write-Host "      Backup: .env.bak.$stamp"

Write-Host '[2/3] Recreando ms-clientes y ms-gateway...'
Invoke-Docker @('compose', 'up', '-d', '--force-recreate', 'ms-clientes', 'ms-gateway')
Wait-Healthy 'ms-clientes'
Wait-Healthy 'ms-gateway'

Write-Host '[3/3] Verificando...'
$health = $null
try { $health = (Invoke-WebRequest -UseBasicParsing 'http://localhost:3080/actuator/health' -TimeoutSec 15).StatusCode } catch { $health = 'ERROR' }
if ($health -ne 200) { throw "Gateway health devolvio $health" }

Write-Host ''
Write-Host 'JWT rotado. Sesiones invalidadas: hay que volver a loguearse.'
