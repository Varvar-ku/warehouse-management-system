# P1-9: adds missing DB role variables to infrastructure\.env with random passwords.
# Existing values are never changed. Safe to run many times.
$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root 'infrastructure\.env'
if (-not (Test-Path $envFile)) {
    throw "File not found: $envFile. Create it first (copy infrastructure\.env.example or run scripts\generate-secrets.ps1)."
}

$services = 'auth', 'catalog', 'warehouse', 'inventory', 'request', 'notification', 'audit', 'report'

function New-RandomPassword {
    $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] 32
    $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $sb = New-Object System.Text.StringBuilder
    foreach ($b in $bytes) { [void]$sb.Append($chars[$b % $chars.Length]) }
    return $sb.ToString()
}

$text = [System.IO.File]::ReadAllText($envFile)
$existing = @{}
foreach ($line in ($text -split "`r?`n")) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=') { $existing[$Matches[1]] = $true }
}

$lines = New-Object System.Collections.ArrayList
foreach ($svc in $services) {
    $up = $svc.ToUpper()
    $wanted = @(
        @{ Name = "${up}_MIGRATION_USER";     Value = "${svc}_owner" },
        @{ Name = "${up}_MIGRATION_PASSWORD"; Value = $null },
        @{ Name = "${up}_DB_USER";            Value = "${svc}_app" },
        @{ Name = "${up}_DB_PASSWORD";        Value = $null }
    )
    foreach ($item in $wanted) {
        if ($existing.ContainsKey($item.Name)) { continue }
        $value = $item.Value
        if ($null -eq $value) { $value = New-RandomPassword }
        [void]$lines.Add("$($item.Name)=$value")
    }
}

if ($lines.Count -eq 0) {
    Write-Host 'All DB role variables are already present in infrastructure\.env. Nothing to do.'
    return
}

$prefix = ''
if ($text.Length -gt 0 -and -not $text.EndsWith("`n")) { $prefix = "`r`n" }
$block = $prefix + '# P1-9: DB roles (added by scripts\generate-db-passwords.ps1)' + "`r`n" + ($lines -join "`r`n") + "`r`n"
[System.IO.File]::AppendAllText($envFile, $block, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Added $($lines.Count) variables to infrastructure\.env."
Write-Host 'Next: scripts\apply-db-roles.ps1 (creates roles in PostgreSQL), then restart the services.'
