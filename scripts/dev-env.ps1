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

foreach ($line in Get-Content $envFile -Encoding UTF8) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $i = $line.IndexOf('=')
    $name  = $line.Substring(0, $i).Trim()
    $value = $line.Substring($i + 1).Trim()
    Set-Item -Path "Env:$name" -Value $value
}
Write-Host "Environment loaded from infrastructure\.env" -ForegroundColor Green

if ($IdeaLine) {
    # Every service gets only its own variables (own DB roles), not the whole .env.
    function Get-DbNames([string]$prefix) {
        return @("${prefix}_DB_USER", "${prefix}_DB_PASSWORD", "${prefix}_MIGRATION_USER", "${prefix}_MIGRATION_PASSWORD")
    }
    $services = [ordered]@{
        'api-gateway'          = @()
        'auth-service'         = (Get-DbNames 'AUTH') + @('BOOTSTRAP_ADMIN_PASSWORD', 'JWT_PRIVATE_KEY_FILE')
        'catalog-service'      = (Get-DbNames 'CATALOG') + @()
        'warehouse-service'    = (Get-DbNames 'WAREHOUSE') + @()
        'inventory-service'    = (Get-DbNames 'INVENTORY') + @()
        'request-service'      = (Get-DbNames 'REQUEST') + @('REQUEST_SERVICE_KEY_FILE')
        'audit-service'        = (Get-DbNames 'AUDIT') + @()
        'notification-service' = (Get-DbNames 'NOTIFICATION') + @()
        'report-service'       = (Get-DbNames 'REPORT') + @()
    }
    Write-Host ""
    Write-Host "IntelliJ environment variables (Run Configuration of each service -> Environment variables):"
    foreach ($svc in $services.Keys) {
        $pairs = @()
        foreach ($n in $services[$svc]) {
            $v = [Environment]::GetEnvironmentVariable($n)
            if ([string]::IsNullOrEmpty($v)) {
                Write-Host ("  missing in .env: " + $n + "  (run scripts\generate-db-passwords.ps1)") -ForegroundColor Yellow
            } else {
                $pairs += "$n=$v"
            }
        }
        Write-Host ""
        Write-Host ("[" + $svc + "]")
        if ($pairs.Count -eq 0) { Write-Host "(no variables needed)" } else { Write-Host ($pairs -join ';') }
    }
}
