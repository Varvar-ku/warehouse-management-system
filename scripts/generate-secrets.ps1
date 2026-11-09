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
$jwt   = New-Secret 48
$admin = New-Secret 20

$text = @(
    "POSTGRES_PASSWORD=$db",
    "DB_PASSWORD=$db",
    "JWT_SECRET=$jwt",
    "BOOTSTRAP_ADMIN_PASSWORD=$admin",
    "WMS_ADMIN_PASSWORD=$admin",
    ""
) -join "`n"

# UTF-8 WITHOUT BOM, otherwise docker compose cannot read the first variable
[System.IO.File]::WriteAllText($envFile, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Written: $envFile" -ForegroundColor Green
Write-Host "Next: .\scripts\rotate-db-password.ps1, then . .\scripts\dev-env.ps1 -IdeaLine"
