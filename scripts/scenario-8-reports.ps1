# scenario-8-reports.ps1
# Checks report-service (movement reports built from Kafka events).
# Uses api.ps1 for login (dot-sourced). ASCII only, PowerShell 5.1.
#
# Usage (from repo root):
#   .\scripts\scenario-8-reports.ps1                    # checks data already collected
#   .\scripts\scenario-8-reports.ps1 -RunScenario5b     # first produces fresh movements via scenario-5b
#
# Needs running: Docker, auth, inventory, request, report, api-gateway.
# Movements made BEFORE the report-service feature are not in the reports (no history), so use -RunScenario5b.

param(
    [string]$Gateway       = 'http://localhost:8080',
    [string]$ReportUrl     = 'http://localhost:8088',
    [string]$StoreLogin    = 'store1',
    [string]$StorePassword = 'Store-12345',
    [string]$AdminLogin    = 'admin',
    [string]$AdminPassword = $env:WMS_ADMIN_PASSWORD,
    [string]$UserLogin     = 'notifuser1',
    [string]$UserPassword  = 'Notif-12345',
    [switch]$RunScenario5b
)

. "$PSScriptRoot\api.ps1"

$ErrorActionPreference = 'Continue'
$script:pass = 0
$script:fail = 0

function Check([string]$name, [bool]$ok, [string]$detail = '') {
    if ($ok) { $script:pass++; Write-Host "PASS  $name" -ForegroundColor Green }
    else     { $script:fail++; Write-Host "FAIL  $name  $detail" -ForegroundColor Red }
}

function Call-Api([string]$Method, [string]$Url, [string]$Token) {
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $status = 0
    $content = $null
    try {
        $resp = Invoke-WebRequest -Uri $Url -Method $Method -Headers $headers -UseBasicParsing -ErrorAction Stop
        $status = [int]$resp.StatusCode
        $content = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    } catch {
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    }
    $json = $null
    if ($content) { try { $json = $content | ConvertFrom-Json } catch { } }
    return [pscustomobject]@{ Status = $status; Json = $json }
}

function Get-Token($auth) {
    if ($null -eq $auth) { return $null }
    if ($auth -is [string]) { return ($auth -replace '^Bearer\s+', '') }
    if ($auth -is [System.Collections.IDictionary]) {
        foreach ($k in $auth.Keys) {
            if ("$k" -ieq 'Authorization') { return (("$($auth[$k])") -replace '^Bearer\s+', '') }
        }
    }
    foreach ($prop in @('accessToken', 'token', 'access_token', 'jwt')) {
        if ($auth.$prop) { return $auth.$prop }
    }
    return $null
}

function Total([string]$path, [string]$token) {
    $r = Call-Api 'GET' "$Gateway/api/v1/reports/$path" $token
    if ($r.Status -ne 200 -or $null -eq $r.Json) { return -1 }
    return [int]$r.Json.totalElements
}

$today    = (Get-Date).ToString('yyyy-MM-dd')
$tomorrow = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$isGuid   = [guid]::Empty

# ---------------------------------------------------------------- 0. Preparation
Write-Host "`n== 0. Preparation ==" -ForegroundColor Cyan

$h = Call-Api 'GET' "$ReportUrl/actuator/health" $null
Check 'report-service health is UP (direct)' ($h.Status -eq 200 -and $h.Json.status -eq 'UP') "status=$($h.Status)"

$adminAuth = Login $AdminLogin $AdminPassword
try { Post "$g/users" @{ login = $UserLogin; password = $UserPassword; fullName = "Notif test $UserLogin"; roles = @('USER') } $adminAuth | Out-Null } catch { }

$tStore = Get-Token (Login $StoreLogin $StorePassword)
$tAdmin = Get-Token $adminAuth
$tUser  = Get-Token (Login $UserLogin $UserPassword)
Check 'login storekeeper, admin and user' ([bool]$tStore -and [bool]$tAdmin -and [bool]$tUser) 'check passwords'
if (-not $tStore) { exit 1 }

if ($RunScenario5b) {
    Write-Host "Running scenario-5b to produce fresh movements ..." -ForegroundColor Yellow
    $before = Total "stock-movements?from=$today&size=1" $tStore
    & "$PSScriptRoot\scenario-5b.ps1" | Out-Null
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline) {
        if ((Total "stock-movements?from=$today&size=1" $tStore) -gt $before) { break }
        Start-Sleep -Milliseconds 700
    }
    Start-Sleep -Seconds 2
}

# ---------------------------------------------------------------- 1. Access control
Write-Host "`n== 1. Access control ==" -ForegroundColor Cyan

$r = Call-Api 'GET' "$Gateway/api/v1/reports/stock-movements" $null
Check 'no token -> 401' ($r.Status -eq 401) "status=$($r.Status)"
$r = Call-Api 'GET' "$Gateway/api/v1/reports/stock-movements" $tUser
Check 'USER role -> 403' ($r.Status -eq 403) "status=$($r.Status)"
$r = Call-Api 'GET' "$Gateway/api/v1/reports/stock-movements" $tStore
Check 'storekeeper -> 200' ($r.Status -eq 200) "status=$($r.Status)"
$r = Call-Api 'GET' "$Gateway/api/v1/reports/stock-movements" $tAdmin
Check 'admin -> 200' ($r.Status -eq 200) "status=$($r.Status)"

# ---------------------------------------------------------------- 2. Movement report
Write-Host "`n== 2. Stock movements ==" -ForegroundColor Cyan

$mv = Call-Api 'GET' "$Gateway/api/v1/reports/stock-movements?from=$today&size=500" $tStore
$rows = @()
if ($mv.Json) { $rows = @($mv.Json.content) }
Check 'movements for today exist (run with -RunScenario5b if not)' ($rows.Count -gt 0) "count=$($rows.Count)"
$types = @($rows | ForEach-Object { $_.movementType } | Sort-Object -Unique)
Check 'RECEIPT movements present' ($types -contains 'RECEIPT') "types=$($types -join ',')"
Check 'ISSUE movements present'   ($types -contains 'ISSUE')   "types=$($types -join ',')"
$bad = @($rows | Where-Object { -not $_.productSku -or -not $_.documentNumber -or $_.quantity -le 0 })
Check 'every row has sku, document number and positive quantity' ($bad.Count -eq 0) "bad=$($bad.Count)"

$named = @($rows | Where-Object { $_.performedByName })
Check 'movement rows carry the performer name (auth directory)' ($named.Count -eq $rows.Count) "named=$($named.Count) of $($rows.Count)"

$r = Call-Api 'GET' "$Gateway/api/v1/reports/stock-movements?type=ISSUE&from=$today&size=500" $tStore
$onlyIssue = @($r.Json.content | Where-Object { $_.movementType -ne 'ISSUE' })
Check 'type=ISSUE filter returns only ISSUE' ($r.Status -eq 200 -and $onlyIssue.Count -eq 0)
$r = Call-Api 'GET' "$Gateway/api/v1/reports/stock-movements?type=BOGUS" $tStore
Check 'unknown type -> 400' ($r.Status -eq 400) "status=$($r.Status)"
$r = Call-Api 'GET' "$Gateway/api/v1/reports/stock-movements?from=$tomorrow&to=$today" $tStore
Check 'from after to -> 400' ($r.Status -eq 400) "status=$($r.Status)"
Check 'period in the future is empty' ((Total "stock-movements?from=$tomorrow" $tStore) -eq 0)

if ($rows.Count -gt 0) {
    $sku = $rows[0].productSku
    $r = Call-Api 'GET' "$Gateway/api/v1/reports/stock-movements?from=$today&sku=$sku&size=500" $tStore
    $other = @($r.Json.content | Where-Object { $_.productSku -notlike "*$sku*" })
    Check 'sku filter returns only matching products' ($r.Status -eq 200 -and $other.Count -eq 0)
}
$r = Call-Api 'GET' "$Gateway/api/v1/reports/stock-movements?from=$today&size=1" $tStore
Check 'size=1 returns at most 1 row, total unchanged' ($r.Json.content.Count -le 1 -and $r.Json.totalElements -eq $mv.Json.totalElements)

# ---------------------------------------------------------------- 3. Receipts
Write-Host "`n== 3. Receipts ==" -ForegroundColor Cyan

$rc = Call-Api 'GET' "$Gateway/api/v1/reports/receipts?from=$today&size=500" $tStore
$rcRows = @()
if ($rc.Json) { $rcRows = @($rc.Json.content) }
Check 'receipts report returns rows' ($rc.Status -eq 200 -and $rcRows.Count -gt 0) "status=$($rc.Status) count=$($rcRows.Count)"
$badRc = @($rcRows | Where-Object { -not $_.documentNumber -or -not $_.supplierName -or -not $_.placedSection })
Check 'receipt rows have document, supplier and placed section' ($badRc.Count -eq 0) "bad=$($badRc.Count)"

# ---------------------------------------------------------------- 4. Issues
Write-Host "`n== 4. Issues ==" -ForegroundColor Cyan

$is = Call-Api 'GET' "$Gateway/api/v1/reports/issues?from=$today&size=500" $tStore
$isRows = @()
if ($is.Json) { $isRows = @($is.Json.content) }
Check 'issues report returns rows' ($is.Status -eq 200 -and $isRows.Count -gt 0) "status=$($is.Status) count=$($isRows.Count)"
$badIs = @($isRows | Where-Object { -not $_.documentNumber -or -not [guid]::TryParse([string]$_.requestId, [ref]$isGuid) -or -not [guid]::TryParse([string]$_.issuedTo, [ref]$isGuid) })
Check 'issue rows have document, request and recipient' ($badIs.Count -eq 0) "bad=$($badIs.Count)"
$noName = @($isRows | Where-Object { -not $_.issuedToName -or -not $_.performedByName })
Check 'issue rows carry recipient and storekeeper names' ($noName.Count -eq 0) "without names=$($noName.Count)"

# ---------------------------------------------------------------- 5. Write-offs
Write-Host "`n== 5. Write-offs ==" -ForegroundColor Cyan

$wo = Call-Api 'GET' "$Gateway/api/v1/reports/write-offs?size=500" $tStore
Check 'write-offs report responds' ($wo.Status -eq 200) "status=$($wo.Status)"
$woRows = @()
if ($wo.Json) { $woRows = @($wo.Json.content) }
if ($woRows.Count -gt 0) {
    $badWo = @($woRows | Where-Object { -not $_.documentNumber -or -not $_.reason })
    Check 'write-off rows have document and reason' ($badWo.Count -eq 0) "bad=$($badWo.Count)"
} else {
    Write-Host "INFO  no write-offs yet (scenario-5b makes none): row content not checked" -ForegroundColor Yellow
}

# ---------------------------------------------------------------- Summary
Write-Host ""
$color = 'Green'
if ($script:fail -gt 0) { $color = 'Red' }
Write-Host "Result: PASS=$($script:pass)  FAIL=$($script:fail)" -ForegroundColor $color
if ($script:fail -gt 0) { exit 1 }
