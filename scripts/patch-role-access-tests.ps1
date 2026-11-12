<#
  patch-role-access-tests.ps1  (ASCII only, PowerShell 5.1)

  P1-10: from now on only inventory-service accepts service tokens, every other service answers 401.
  The access matrix (RoleAccessTestSupport) of the other modules still expects 403 for the SERVICE role.
  This script adds the same hook that is already in auth-service:
      boolean acceptsServiceTokens()     (false by default, true in inventory-service)
  and makes the matrix expect 401 for SERVICE where the hook returns false.

  Run from the repository root:  .\scripts\patch-role-access-tests.ps1
  Safe to run twice (files that already have the hook are skipped). Stops with a message if a file
  does not look as expected, nothing is written for that file.
#>
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$anchorMethod = 'abstract List<Endpoint> endpoints();'
$anchorCheck  = 'boolean allowed = e.allowed().contains(role);'

function Count-Occurrences([string]$text, [string]$what) {
    return $text.Split(@($what), [StringSplitOptions]::None).Count - 1
}

$files = @()
foreach ($d in (Get-ChildItem -Directory -Filter '*-service')) {
    $testDir = Join-Path $d.FullName 'src\test'
    if (Test-Path $testDir) {
        $files += Get-ChildItem -Recurse -File -Filter 'RoleAccessTestSupport.java' -Path $testDir
    }
}
if ($files.Count -eq 0) { throw 'No RoleAccessTestSupport.java found. Run the script from the repository root.' }

foreach ($f in $files) {
    $text = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $rel = $f.FullName.Substring((Get-Location).Path.Length + 1)

    if ($text.Contains('acceptsServiceTokens')) { Write-Host ("skip (already patched): " + $rel) -ForegroundColor Yellow; continue }
    if ((Count-Occurrences $text $anchorMethod) -ne 1 -or (Count-Occurrences $text $anchorCheck) -ne 1) {
        Write-Host ("ERROR  " + $rel + ": unexpected content, patch it by hand (see docs in this script)") -ForegroundColor Red
        continue
    }

    $accepts = 'false'
    if ($f.FullName -match 'inventory-service') { $accepts = 'true' }
    $nl = "`n"
    if ($text.Contains("`r`n")) { $nl = "`r`n" }

    $method = @(
        '',
        '    /**',
        '     * P1-10: whether this service accepts service tokens (app.security.accept-service-tokens).',
        '     * Only inventory-service does; everywhere else the SERVICE role must get 401 on every endpoint.',
        '     */',
        '    boolean acceptsServiceTokens() {',
        ('        return ' + $accepts + ';'),
        '    }'
    ) -join $nl

    $block = @(
        'if (SERVICE.equals(role) && !acceptsServiceTokens()) {',
        '                    // P1-10: only inventory-service accepts service tokens, others reject them with 401 (ServiceTokenPolicy)',
        '                    if (status != 401) {',
        '                        violations.add("SERVICE token must be rejected with 401, got " + status + ": " + result.shortBody());',
        '                    }',
        '                    continue;',
        '                }',
        '                boolean allowed = e.allowed().contains(role);'
    ) -join $nl

    $text = $text.Replace($anchorMethod, $anchorMethod + $nl + $method).Replace($anchorCheck, $block)
    [System.IO.File]::WriteAllText($f.FullName, $text, $utf8)
    Write-Host ("patched: " + $rel + "  (acceptsServiceTokens = " + $accepts + ")") -ForegroundColor Green
}
