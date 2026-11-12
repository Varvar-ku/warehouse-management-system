# Sets the PostgreSQL password of user "warehouse" to POSTGRES_PASSWORD from infrastructure\.env.
# Needed once for an EXISTING volume: POSTGRES_PASSWORD in docker-compose applies only to a fresh volume.
# The container must be running. ASCII only.
$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root 'infrastructure\.env'
if (-not (Test-Path $envFile)) { throw "No infrastructure\.env. Run generate-secrets.ps1 first." }

$pwd = $null
foreach ($line in Get-Content $envFile) {
    if ($line -match '^POSTGRES_PASSWORD=(.+)$') { $pwd = $Matches[1].Trim() }
}
if (-not $pwd) { throw "POSTGRES_PASSWORD not found in infrastructure\.env" }
if ($pwd -notmatch '^[A-Za-z0-9]+$') { throw "Password must be letters and digits only (quoting safety)" }

"ALTER USER warehouse WITH PASSWORD '$pwd';" | docker exec -i warehouse-postgres psql -U warehouse -d postgres
if ($LASTEXITCODE -ne 0) { throw "ALTER USER failed" }
Write-Host "Superuser password changed. Next: .\scripts\apply-db-roles.ps1 (service roles), then restart the services." -ForegroundColor Green
