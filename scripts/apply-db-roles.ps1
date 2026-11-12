# P1-9: creates/updates DB roles in the running PostgreSQL container (for an existing data volume).
# On a brand new volume the same script runs automatically (infrastructure\postgres\init\02-roles.sh).
# Safe to run many times; it also applies password changes from infrastructure\.env.
$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root 'infrastructure\docker-compose.yaml'

function Invoke-Native([string]$What, [scriptblock]$Block) {
    & $Block
    if ($LASTEXITCODE -ne 0) { throw "$What failed (exit code $LASTEXITCODE)" }
}

# Recreate the container so it receives the new variables (data volume is kept).
Invoke-Native 'docker compose up' { docker compose -f $compose up -d postgres }

$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    $status = docker inspect -f '{{.State.Health.Status}}' warehouse-postgres
    if ($status -eq 'healthy') { $healthy = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $healthy) { throw 'PostgreSQL container did not become healthy in 60 seconds.' }

Invoke-Native 'role setup' { docker compose -f $compose exec -T postgres sh /docker-entrypoint-initdb.d/02-roles.sh }

Write-Host 'Done. Restart the services: Flyway now runs as <svc>_owner, the application runs as <svc>_app.'
