<#
  check-migrations.ps1  (ASCII only, PowerShell 5.1)

  Checks Flyway migration files of every *-service module:
    - version numbers are unique inside a service
    - numbering has no gaps (warning)
    - no template leftovers ("Rename this file") except in the known applied files
    - no zip archives under src/main/resources
  Run from the repository root:  .\scripts\check-migrations.ps1
  Exit code 1 when an error is found.
#>
$ErrorActionPreference = 'Stop'

# Applied files that keep a misleading comment (see docs/migrations-notes.md)
$known = @(
    'inventory-service/src/main/resources/db/migration/V4__idempotency_owner.sql',
    'request-service/src/main/resources/db/migration/V3__outbox_event.sql'
)

$errors = 0
$warnings = 0
$root = (Get-Location).Path

$dirs = Get-ChildItem -Directory -Path $root -Filter '*-service' |
    Where-Object { Test-Path (Join-Path $_.FullName 'src\main\resources\db\migration') }

foreach ($d in $dirs) {
    $migDir = Join-Path $d.FullName 'src\main\resources\db\migration'
    $files = Get-ChildItem -File -Path $migDir -Filter '*.sql'
    $versions = @{}
    foreach ($f in $files) {
        $m = [regex]::Match($f.Name, '^V(\d+)__(.+)\.sql$')
        if (-not $m.Success) {
            Write-Host ("ERROR  " + $d.Name + ": bad file name " + $f.Name) -ForegroundColor Red
            $errors++
            continue
        }
        $v = [int]$m.Groups[1].Value
        if ($versions.ContainsKey($v)) {
            Write-Host ("ERROR  " + $d.Name + ": version V" + $v + " used twice (" + $versions[$v] + ", " + $f.Name + ")") -ForegroundColor Red
            $errors++
        } else {
            $versions[$v] = $f.Name
        }
        $rel = ($d.Name + '/src/main/resources/db/migration/' + $f.Name)
        if ($known -notcontains $rel) {
            if (Select-String -Path $f.FullName -Pattern 'Rename this file' -Quiet) {
                Write-Host ("ERROR  " + $rel + ": template comment 'Rename this file' left in the file") -ForegroundColor Red
                $errors++
            }
        }
    }
    if ($versions.Count -gt 0) {
        $max = ($versions.Keys | Measure-Object -Maximum).Maximum
        for ($i = 1; $i -le $max; $i++) {
            if (-not $versions.ContainsKey($i)) {
                Write-Host ("WARN   " + $d.Name + ": gap, no V" + $i) -ForegroundColor Yellow
                $warnings++
            }
        }
        Write-Host ("OK     " + $d.Name + ": " + $versions.Count + " migrations, last V" + $max) -ForegroundColor Green
    }
    $zips = Get-ChildItem -Recurse -File -Path (Join-Path $d.FullName 'src\main\resources') -Filter '*.zip' -ErrorAction SilentlyContinue
    foreach ($z in $zips) {
        Write-Host ("ERROR  " + $d.Name + ": archive inside resources: " + $z.FullName) -ForegroundColor Red
        $errors++
    }
}

Write-Host ''
Write-Host ("errors: " + $errors + ", warnings: " + $warnings)
if ($errors -gt 0) { exit 1 }
