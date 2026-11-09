# scenario-10-requests-discrepancies.ps1
# Checks the "Requests" and "Discrepancies" reports of report-service.
# Uses api.ps1 for login. ASCII only, PowerShell 5.1.
#
# Usage (from repo root):
#   .\scripts\scenario-10-requests-discrepancies.ps1 -RunScenario5b
#
# Needs running: Docker, auth, inventory, request, report, api-gateway.
# Events produced BEFORE the request-service/inventory changes have no quantities; use -RunScenario5b.

param(
    [string]$Gateway       = 'http://localhost:8080',
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

# ---------------------------------------------------------------- 0. Preparation
Write-Host "`n== 0. Preparation ==" -ForegroundColor Cyan

$adminAuth = Login $AdminLogin $AdminPassword
try { Post "$g/users" @{ login = $UserLogin; password = $UserPassword; fullName = "Notif test $UserLogin"; roles = @('USER') } $adminAuth | Out-Null } catch { }
$tStore = Get-Token (Login $StoreLogin $StorePassword)
$tAdmin = Get-Token $adminAuth
$tUser  = Get-Token (Login $UserLogin $UserPassword)
Check 'login storekeeper, admin and user' ([bool]$tStore -and [bool]$tAdmin -and [bool]$tUser) 'check passwords'
if (-not $tStore) { exit 1 }

if ($RunScenario5b) {
    Write-Host "Running scenario-5b to produce fresh requests and discrepancies ..." -ForegroundColor Yellow
    $beforeCancelled = Total "requests?from=$today&status=CANCELLED&size=1" $tStore
    $beforeDisc = Total "discrepancies?from=$today&source=REQUEST&size=1" $tStore
    & "$PSScriptRoot\scenario-5b.ps1" | Out-Null
    $deadline = (Get-Date).AddSeconds(40)
    while ((Get-Date) -lt $deadline) {
        if ((Total "requests?from=$today&status=CANCELLED&size=1" $tStore) -gt $beforeCancelled -and
            (Total "discrepancies?from=$today&source=REQUEST&size=1" $tStore) -gt $beforeDisc) { break }
        Start-Sleep -Milliseconds 700
    }
    Start-Sleep -Seconds 2
}

# ---------------------------------------------------------------- 1. Access control
Write-Host "`n== 1. Access control ==" -ForegroundColor Cyan

foreach ($p in @('requests', 'discrepancies')) {
    $r = Call-Api 'GET' "$Gateway/api/v1/reports/$p" $null
    Check "${p}:: no token -> 401" ($r.Status -eq 401) "status=$($r.Status)"
    $r = Call-Api 'GET' "$Gateway/api/v1/reports/$p" $tUser
    Check "${p}:: USER role -> 403" ($r.Status -eq 403) "status=$($r.Status)"
    $r = Call-Api 'GET' "$Gateway/api/v1/reports/$p" $tStore
    Check "${p}:: storekeeper -> 200" ($r.Status -eq 200) "status=$($r.Status)"
    $r = Call-Api 'GET' "$Gateway/api/v1/reports/$p" $tAdmin
    Check "${p}:: admin -> 200" ($r.Status -eq 200) "status=$($r.Status)"
}

# ---------------------------------------------------------------- 2. Requests report
Write-Host "`n== 2. Requests ==" -ForegroundColor Cyan

$rq = Call-Api 'GET' "$Gateway/api/v1/reports/requests?from=$today&size=500" $tStore
$rows = @()
if ($rq.Json) { $rows = @($rq.Json.content) }
Check 'requests created today exist (run with -RunScenario5b if not)' ($rows.Count -gt 0) "count=$($rows.Count)"
$noName = @($rows | Where-Object { -not $_.requesterName })
Check 'every row carries the requester name' ($rows.Count -gt 0 -and $noName.Count -eq 0) "without name=$($noName.Count)"

$closed = @($rows | Where-Object { $_.status -eq 'CLOSED' -and $_.requestedQuantity -eq 60 -and $_.issuedQuantity -eq 59 })
Check 'a CLOSED request: requested 60, issued 59 (scenario-5b)' ($closed.Count -gt 0) "found=$($closed.Count)"
if ($closed.Count -gt 0) {
    $c = $closed[0]
    Check 'CLOSED row has items count, issue number and closing date' ($c.itemsCount -ge 1 -and $c.issueNumber -like 'IS-*' -and $c.closedAt) "items=$($c.itemsCount) issue=$($c.issueNumber) closed=$($c.closedAt)"
    Check 'created date is not after closing date' ([datetime]$c.createdAt -le [datetime]$c.closedAt)
    Check 'reserved quantity is filled' ($null -ne $c.reservedQuantity -and $c.reservedQuantity -ge $c.issuedQuantity)
}
Check 'a CANCELLED request exists' (@($rows | Where-Object { $_.status -eq 'CANCELLED' }).Count -gt 0)

$r = Call-Api 'GET' "$Gateway/api/v1/reports/requests?from=$today&status=closed&size=500" $tStore
$other = @($r.Json.content | Where-Object { $_.status -ne 'CLOSED' })
Check 'status filter returns only that status (case-insensitive)' ($r.Status -eq 200 -and $r.Json.totalElements -gt 0 -and $other.Count -eq 0)
if ($rows.Count -gt 0) {
    $num = $rows[0].number
    $r = Call-Api 'GET' "$Gateway/api/v1/reports/requests?number=$num" $tStore
    $wrong = @($r.Json.content | Where-Object { $_.number -notlike "*$num*" })
    Check 'number filter finds the request' ($r.Status -eq 200 -and $r.Json.totalElements -ge 1 -and $wrong.Count -eq 0)
}
$r = Call-Api 'GET' "$Gateway/api/v1/reports/requests?from=$tomorrow&to=$today" $tStore
Check 'from after to -> 400' ($r.Status -eq 400) "status=$($r.Status)"
Check 'period in the future is empty' ((Total "requests?from=$tomorrow" $tStore) -eq 0)

# ---------------------------------------------------------------- 3. Discrepancies report
Write-Host "`n== 3. Discrepancies ==" -ForegroundColor Cyan

$ds = Call-Api 'GET' "$Gateway/api/v1/reports/discrepancies?from=$today&source=REQUEST&size=500" $tStore
$dRows = @()
if ($ds.Json) { $dRows = @($ds.Json.content) }
Check 'request discrepancies exist' ($ds.Status -eq 200 -and $dRows.Count -gt 0) "status=$($ds.Status) count=$($dRows.Count)"
$short = @($dRows | Where-Object { $_.requiredQuantity -eq 60 -and $_.actualQuantity -eq 59 })
Check 'shortage row: required 60, actual 59, deviation -1' ($short.Count -gt 0 -and $short[0].deviation -eq -1) "found=$($short.Count)"
if ($short.Count -gt 0) {
    $s = $short[0]
    Check 'shortage row has the reason from picking' ([bool]$s.reason) "reason=$($s.reason)"
    Check 'shortage row has request number, product and issue number' ($s.documentNumber -like 'RQ-*' -and $s.productSku -and $s.issueNumber -like 'IS-*') "doc=$($s.documentNumber) sku=$($s.productSku) issue=$($s.issueNumber)"
    Check 'shortage row has the responsible name' ([bool]$s.responsibleName)
}
$wrongSrc = @($dRows | Where-Object { $_.source -ne 'REQUEST' })
Check 'source filter returns only REQUEST rows' ($wrongSrc.Count -eq 0)
$r = Call-Api 'GET' "$Gateway/api/v1/reports/discrepancies?source=bogus" $tStore
Check 'unknown source -> 400' ($r.Status -eq 400) "status=$($r.Status)"
$r = Call-Api 'GET' "$Gateway/api/v1/reports/discrepancies?from=$tomorrow&to=$today" $tStore
Check 'from after to -> 400' ($r.Status -eq 400) "status=$($r.Status)"
Check 'period in the future is empty' ((Total "discrepancies?from=$tomorrow" $tStore) -eq 0)
$r = Call-Api 'GET' "$Gateway/api/v1/reports/discrepancies?unresolvedOnly=true&size=500" $tStore
$bad = @($r.Json.content | Where-Object { $_.source -ne 'RECEIPT' -or $_.resolution })
Check 'unresolvedOnly returns only unresolved receipt rows' ($r.Status -eq 200 -and $bad.Count -eq 0)

$rc = Call-Api 'GET' "$Gateway/api/v1/reports/discrepancies?source=RECEIPT&size=500" $tStore
$rcRows = @()
if ($rc.Json) { $rcRows = @($rc.Json.content) }
if ($rcRows.Count -gt 0) {
    $badRc = @($rcRows | Where-Object { $null -eq $_.requiredQuantity -or $null -eq $_.actualQuantity -or -not $_.documentNumber })
    Check 'receipt discrepancy rows have document, planned and actual quantity' ($badRc.Count -eq 0) "bad=$($badRc.Count)"
} else {
    Write-Host "INFO  no receipt discrepancies yet (scenario-5b has none): run a placement with a different section or quantity to see one" -ForegroundColor Yellow
}

# ---------------------------------------------------------------- Summary
Write-Host ""
$color = 'Green'
if ($script:fail -gt 0) { $color = 'Red' }
Write-Host "Result: PASS=$($script:pass)  FAIL=$($script:fail)" -ForegroundColor $color
if ($script:fail -gt 0) { exit 1 }
