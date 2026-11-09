# scenario-9-picking-report.ps1
# Checks picking reports of report-service (journal of operations + worker summary),
# built from picking.* audit events. Uses api.ps1 for login. ASCII only, PowerShell 5.1.
#
# Usage (from repo root):
#   .\scripts\scenario-9-picking-report.ps1 -RunScenario5b
#
# Needs running: Docker, auth, inventory, request, report, api-gateway.

param(
    [string]$Gateway       = 'http://localhost:8080',
    [string]$StoreLogin    = 'store1',
    [string]$StorePassword = 'Store-12345',
    [string]$AdminLogin    = 'admin',
    [string]$AdminPassword = 'Admin-2026-dev',
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

$today = (Get-Date).ToString('yyyy-MM-dd')

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
    Write-Host "Running scenario-5b to produce fresh picking events ..." -ForegroundColor Yellow
    $before = Total "picking/events?from=$today&size=1" $tStore
    & "$PSScriptRoot\scenario-5b.ps1" | Out-Null
    $deadline = (Get-Date).AddSeconds(40)
    while ((Get-Date) -lt $deadline) {
        if ((Total "picking/events?from=$today&action=picking.task_rejected&size=1" $tStore) -gt 0 -and
            (Total "picking/events?from=$today&size=1" $tStore) -gt $before) { break }
        Start-Sleep -Milliseconds 700
    }
    Start-Sleep -Seconds 2
}

# ---------------------------------------------------------------- 1. Access control
Write-Host "`n== 1. Access control ==" -ForegroundColor Cyan

$r = Call-Api 'GET' "$Gateway/api/v1/reports/picking/events" $null
Check 'no token -> 401' ($r.Status -eq 401) "status=$($r.Status)"
$r = Call-Api 'GET' "$Gateway/api/v1/reports/picking/events" $tUser
Check 'USER role -> 403' ($r.Status -eq 403) "status=$($r.Status)"
$r = Call-Api 'GET' "$Gateway/api/v1/reports/picking/workers" $tUser
Check 'USER role on workers report -> 403' ($r.Status -eq 403) "status=$($r.Status)"
$r = Call-Api 'GET' "$Gateway/api/v1/reports/picking/events" $tAdmin
Check 'admin -> 200' ($r.Status -eq 200) "status=$($r.Status)"

# ---------------------------------------------------------------- 2. Journal
Write-Host "`n== 2. Journal of operations ==" -ForegroundColor Cyan

$ev = Call-Api 'GET' "$Gateway/api/v1/reports/picking/events?from=$today&size=500" $tStore
$rows = @()
if ($ev.Json) { $rows = @($ev.Json.content) }
Check 'journal has events for today (run with -RunScenario5b if not)' ($ev.Status -eq 200 -and $rows.Count -gt 0) "status=$($ev.Status) count=$($rows.Count)"

$actions = @($rows | ForEach-Object { $_.action } | Sort-Object -Unique)
foreach ($a in @('picking.task_created', 'picking.worker_added', 'picking.items_assigned', 'picking.item_picked',
                 'picking.task_completed', 'picking.worker_removed', 'picking.task_rejected')) {
    Check "journal contains $a" ($actions -contains $a) "actions=$($actions -join ',')"
}

$noActor = @($rows | Where-Object { -not $_.actorName })
Check 'every row carries the actor name' ($rows.Count -gt 0 -and $noActor.Count -eq 0) "without name=$($noActor.Count)"

$picked = @($rows | Where-Object { $_.action -eq 'picking.item_picked' })
$badPicked = @($picked | Where-Object { $_.pickedQuantity -gt $_.quantity -or $_.pickedQuantity -lt 0 -or -not $_.workerName })
Check 'item_picked rows: picked <= assigned and worker named' ($picked.Count -gt 0 -and $badPicked.Count -eq 0) "rows=$($picked.Count) bad=$($badPicked.Count)"
$short = @($picked | Where-Object { $_.shortage -eq $true -and $_.note })
Check 'a shortage row has its reason (scenario-5b: worker2 short by 1)' ($short.Count -gt 0) "shortage rows with note=$($short.Count)"

$removed = @($rows | Where-Object { $_.action -eq 'picking.worker_removed' })
$badRemoved = @($removed | Where-Object { -not $_.workerName -or -not $_.replaceWithName -or $_.quantity -le 0 })
Check 'worker_removed rows: worker, replacement and moved quantity' ($removed.Count -gt 0 -and $badRemoved.Count -eq 0) "rows=$($removed.Count) bad=$($badRemoved.Count)"

$assigned = @($rows | Where-Object { $_.action -eq 'picking.items_assigned' })
$badAssigned = @($assigned | Where-Object { -not $_.workerName -or $_.quantity -le 0 })
Check 'items_assigned rows: worker and quantity' ($assigned.Count -gt 0 -and $badAssigned.Count -eq 0) "rows=$($assigned.Count) bad=$($badAssigned.Count)"

# filters
$r = Call-Api 'GET' "$Gateway/api/v1/reports/picking/events?from=$today&action=picking.item_picked&size=500" $tStore
$other = @($r.Json.content | Where-Object { $_.action -ne 'picking.item_picked' })
Check 'action filter returns only that action' ($r.Status -eq 200 -and $r.Json.totalElements -gt 0 -and $other.Count -eq 0)
$r = Call-Api 'GET' "$Gateway/api/v1/reports/picking/events?action=bogus" $tStore
Check 'unknown action -> 400' ($r.Status -eq 400) "status=$($r.Status)"
$tomorrow = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$r = Call-Api 'GET' "$Gateway/api/v1/reports/picking/events?from=$tomorrow&to=$today" $tStore
Check 'from after to -> 400' ($r.Status -eq 400) "status=$($r.Status)"
Check 'period in the future is empty' ((Total "picking/events?from=$tomorrow" $tStore) -eq 0)

if ($rows.Count -gt 0) {
    $task = ($rows | Where-Object { $_.taskNumber } | Select-Object -First 1).taskNumber
    $r = Call-Api 'GET' "$Gateway/api/v1/reports/picking/events?taskNumber=$task&size=500" $tStore
    $wrongTask = @($r.Json.content | Where-Object { $_.taskNumber -ne $task })
    Check 'taskNumber filter returns only that task' ($r.Status -eq 200 -and $r.Json.totalElements -gt 0 -and $wrongTask.Count -eq 0)
}

# ---------------------------------------------------------------- 3. Worker summary
Write-Host "`n== 3. Worker summary ==" -ForegroundColor Cyan

$wk = Call-Api 'GET' "$Gateway/api/v1/reports/picking/workers?from=$today" $tStore
$wRows = @()
if ($wk.Json) { $wRows = @($wk.Json.content) }
Check 'workers report returns rows' ($wk.Status -eq 200 -and $wRows.Count -ge 2) "status=$($wk.Status) count=$($wRows.Count)"
$noName = @($wRows | Where-Object { -not $_.workerName })
Check 'every worker row carries the name' ($wRows.Count -gt 0 -and $noName.Count -eq 0) "without name=$($noName.Count)"
$bad = @($wRows | Where-Object { $_.pickedQuantity -gt $_.assignedQuantity })
Check 'picked never exceeds assigned' ($wRows.Count -gt 0 -and $bad.Count -eq 0) "bad=$($bad.Count)"
Check 'some worker has a shortage row' (@($wRows | Where-Object { $_.shortageRows -ge 1 }).Count -ge 1)
Check 'some worker was removed with rows moved' (@($wRows | Where-Object { $_.removals -ge 1 -and $_.movedQuantity -gt 0 }).Count -ge 1)

if ($wRows.Count -gt 0) {
    $wid = $wRows[0].workerId
    $r = Call-Api 'GET' "$Gateway/api/v1/reports/picking/workers?from=$today&workerId=$wid" $tStore
    Check 'workerId filter returns only that worker' ($r.Status -eq 200 -and @($r.Json.content).Count -eq 1 -and $r.Json.content[0].workerId -eq $wid)
}

# ---------------------------------------------------------------- Summary
Write-Host ""
$color = 'Green'
if ($script:fail -gt 0) { $color = 'Red' }
Write-Host "Result: PASS=$($script:pass)  FAIL=$($script:fail)" -ForegroundColor $color
if ($script:fail -gt 0) { exit 1 }
