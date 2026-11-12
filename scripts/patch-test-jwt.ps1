<#
  patch-test-jwt.ps1  (ASCII only, PowerShell 5.1)

  P2-11: JWT is now signed with RS256 (key pair) instead of the shared HS256 secret.
  auth-service tests are already updated in the archive. This script updates the integration test base of the
  OTHER modules the same way:
    * TestTokens.java is replaced by scripts\templates\TestTokens.java.template (package name is kept);
    * in IntegrationTestBase.java the line  registry.add("app.security.jwt.secret", () -> TestTokens.SECRET);
      is replaced by the key-file properties (request-service also gets the service-token signing key,
      inventory-service trusts the test key for service tokens).

  Run from the repository root:  .\scripts\patch-test-jwt.ps1
  Safe to run twice (patched files are skipped). A file that does not look as expected is NOT changed:
  the script prints ERROR and you patch that module by hand.
#>
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$templatePath = Join-Path $PSScriptRoot 'templates\TestTokens.java.template'
if (-not (Test-Path $templatePath)) { throw "Template not found: $templatePath" }
$template = [System.IO.File]::ReadAllText($templatePath, [System.Text.Encoding]::UTF8).Replace("`r`n", "`n")

$oldLine = 'registry.add("app.security.jwt.secret", () -> TestTokens.SECRET);'

function Get-Newline([string]$text) {
    if ($text.Contains("`r`n")) { return "`r`n" }
    return "`n"
}

$bases = @()
foreach ($d in (Get-ChildItem -Directory -Filter '*-service')) {
    $testDir = Join-Path $d.FullName 'src\test'
    if (Test-Path $testDir) {
        $bases += Get-ChildItem -Recurse -File -Filter 'IntegrationTestBase.java' -Path $testDir
    }
}
if ($bases.Count -eq 0) { throw 'No IntegrationTestBase.java found. Run the script from the repository root.' }

foreach ($base in $bases) {
    $rel = $base.FullName.Substring((Get-Location).Path.Length + 1)
    $module = ($rel -split '[\\/]')[0]
    $dir = $base.DirectoryName
    $tokensFile = Join-Path $dir 'TestTokens.java'

    $text = [System.IO.File]::ReadAllText($base.FullName, [System.Text.Encoding]::UTF8)
    if ($text.Contains('app.security.jwt.public-key-file')) {
        Write-Host ("skip (already patched): " + $rel) -ForegroundColor Yellow
        continue
    }
    $parts = $text.Split(@($oldLine), [StringSplitOptions]::None)
    if ($parts.Count -ne 2) {
        Write-Host ("ERROR  " + $rel + ": line with app.security.jwt.secret not found exactly once, patch by hand") -ForegroundColor Red
        continue
    }
    if (-not (Test-Path $tokensFile)) {
        Write-Host ("ERROR  " + $module + ": TestTokens.java not found next to IntegrationTestBase.java") -ForegroundColor Red
        continue
    }
    $old = [System.IO.File]::ReadAllText($tokensFile, [System.Text.Encoding]::UTF8)
    $missing = @()
    foreach ($m in 'static String user(', 'static String service(', 'static String expired(', 'static String wrongSignature(', 'static String wrongIssuer(') {
        if (-not $old.Contains($m)) { $missing += $m }
    }
    if ($missing.Count -gt 0 -or $old -notmatch 'package\s+([\w\.]+)\s*;') {
        Write-Host ("ERROR  " + $module + ": TestTokens.java differs from the expected one (missing: " + ($missing -join ', ') + "), patch by hand") -ForegroundColor Red
        continue
    }
    $package = $Matches[1]

    $nl = Get-Newline $text
    $lines = @(
        'registry.add("app.security.jwt.public-key-file", TestTokens::publicKeyFile);'
    )
    if ($module -eq 'auth-service') {
        $lines += 'registry.add("app.security.jwt.private-key-file", TestTokens::privateKeyFile);'
    }
    if ($module -eq 'request-service') {
        $lines += 'registry.add("app.security.service-token.signing-key-file", TestTokens::privateKeyFile);'
    }
    if ($module -eq 'inventory-service') {
        $lines += 'registry.add("app.security.service-token.trusted.request-service.public-key-file", TestTokens::publicKeyFile);'
    }
    $indent = '        '
    $newText = $parts[0] + '// P2-11: RS256, test key pair from TestTokens' + $nl + $indent + ($lines -join ($nl + $indent)) + $parts[1]

    $tokensNl = Get-Newline $old
    $newTokens = $template.Replace('%PACKAGE%', $package).Replace("`n", $tokensNl)

    [System.IO.File]::WriteAllText($base.FullName, $newText, $utf8)
    [System.IO.File]::WriteAllText($tokensFile, $newTokens, $utf8)
    Write-Host ("patched: " + $module + " (IntegrationTestBase.java, TestTokens.java)") -ForegroundColor Green
}

# other tests that still use the old secret
$leftovers = @()
foreach ($d in (Get-ChildItem -Directory -Filter '*-service')) {
    $testDir = Join-Path $d.FullName 'src\test'
    if (Test-Path $testDir) {
        $leftovers += Get-ChildItem -Recurse -File -Filter '*.java' -Path $testDir |
            Select-String -Pattern 'TestTokens\.SECRET|app\.security\.jwt\.secret|HmacSHA256|MacAlgorithm'
    }
}
foreach ($l in $leftovers) {
    Write-Host ("CHECK  " + $l.Path.Substring((Get-Location).Path.Length + 1) + ":" + $l.LineNumber + "  " + $l.Line.Trim()) -ForegroundColor Yellow
}
