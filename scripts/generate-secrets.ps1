# Generates random secrets into infrastructure\.env (the file is in .gitignore).
# Usage (from repo root):  .\scripts\generate-secrets.ps1        (use -Force to overwrite)
# ASCII only.
param([switch]$Force)
$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root 'infrastructure\.env'
if ((Test-Path $envFile) -and -not $Force) {
    Write-Host "infrastructure\.env already exists. Use -Force to overwrite." -ForegroundColor Yellow
    exit 1
}

function New-Secret([int]$length) {
    $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $sb = New-Object System.Text.StringBuilder
    foreach ($b in $bytes) { [void]$sb.Append($chars[$b % $chars.Length]) }
    $sb.ToString()
}

$db    = New-Secret 24
$admin = New-Secret 20

$lines = New-Object System.Collections.ArrayList
[void]$lines.Add("POSTGRES_PASSWORD=$db")      # superuser "warehouse": administration only, services do not use it
# P2-11: JWT is signed with RS256. Private keys live OUTSIDE the repository (your user profile).
# The files themselves are created by the services in the dev profile on first start.
$keyDir = Join-Path $env:USERPROFILE '.wms\keys'
New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
[void]$lines.Add("JWT_PRIVATE_KEY_FILE=" + (Join-Path $keyDir 'jwt-private.pem'))
[void]$lines.Add("REQUEST_SERVICE_KEY_FILE=" + (Join-Path $keyDir 'request-service-private.pem'))
[void]$lines.Add("BOOTSTRAP_ADMIN_PASSWORD=$admin")
[void]$lines.Add("WMS_ADMIN_PASSWORD=$admin")

# P1-9: every service has its own DB roles: <svc>_owner (Flyway migrations) and <svc>_app (runtime)
foreach ($svc in 'auth', 'catalog', 'warehouse', 'inventory', 'request', 'notification', 'audit', 'report') {
    $up = $svc.ToUpper()
    [void]$lines.Add("${up}_MIGRATION_USER=${svc}_owner")
    [void]$lines.Add("${up}_MIGRATION_PASSWORD=$(New-Secret 24)")
    [void]$lines.Add("${up}_DB_USER=${svc}_app")
    [void]$lines.Add("${up}_DB_PASSWORD=$(New-Secret 24)")
}
[void]$lines.Add("")
$text = $lines -join "`n"

# UTF-8 WITHOUT BOM, otherwise docker compose cannot read the first variable
[System.IO.File]::WriteAllText($envFile, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Written: $envFile" -ForegroundColor Green
Write-Host "Next: new volume - cd infrastructure; docker compose up -d (roles are created automatically)."
Write-Host "      existing volume - .\scripts\rotate-db-password.ps1, then .\scripts\apply-db-roles.ps1."
Write-Host "Then: . .\scripts\dev-env.ps1 -IdeaLine"
