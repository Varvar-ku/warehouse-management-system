# Loads infrastructure\.env into the CURRENT PowerShell session (dot-source it):
#   . .\scripts\dev-env.ps1
#   . .\scripts\dev-env.ps1 -IdeaLine     (also prints a line for IntelliJ: Run Configuration -> Environment variables)
# ASCII only.
param([switch]$IdeaLine)

$root    = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root 'infrastructure\.env'
if (-not (Test-Path $envFile)) {
    Write-Host "No infrastructure\.env. Run .\scripts\generate-secrets.ps1 first." -ForegroundColor Red
    return
}

foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $i = $line.IndexOf('=')
    $name  = $line.Substring(0, $i).Trim()
    $value = $line.Substring($i + 1).Trim()
    Set-Item -Path "Env:$name" -Value $value
}
Write-Host "Environment loaded from infrastructure\.env" -ForegroundColor Green

if ($IdeaLine) {
    $names = 'JWT_SECRET', 'DB_PASSWORD', 'BOOTSTRAP_ADMIN_PASSWORD'
    $pairs = $names | ForEach-Object { "$_=" + (Get-Item "Env:$_").Value }
    Write-Host ""
    Write-Host "IntelliJ environment variables (paste into every service run configuration):"
    Write-Host ($pairs -join ';')
}
