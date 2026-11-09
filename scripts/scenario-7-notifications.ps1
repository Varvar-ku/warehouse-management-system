# scenario-7-notifications.ps1
# Checks notification-service: events from Kafka -> notifications -> REST API.
# Uses api.ps1 for login (dot-sourced), creates its own test users via admin.
# ASCII only, PowerShell 5.1.
#
# Usage (from repo root, no arguments needed):
#   .\scripts\scenario-7-notifications.ps1
# Optional: -KafkaContainer <name>  (auto-detected by name containing "kafka")
#
# Needs running: Docker (Postgres + Kafka), auth, notification, api-gateway.

param(
    [string]$Gateway         = 'http://localhost:8080',
    [string]$NotificationUrl = 'http://localhost:8087',
    [string]$AdminLogin      = 'admin',
    [string]$AdminPassword = $env:WMS_ADMIN_PASSWORD,
    [string]$TestLogin1      = 'notifuser1',
    [string]$TestLogin2      = 'notifuser2',
    [string]$TestPassword    = 'Notif-12345',
    [string]$KafkaContainer  = '',
    [string]$KafkaBootstrap  = 'localhost:9092',
    [string]$Topic           = 'request-events'
)

# api.ps1 only defines functions: it must be dot-sourced (note the leading ". ")
. "$PSScriptRoot\api.ps1"

$ErrorActionPreference = 'Continue'
$script:pass = 0
$script:fail = 0

function Check([string]$name, [bool]$ok, [string]$detail = '') {
    if ($ok) { $script:pass++; Write-Host "PASS  $name" -ForegroundColor Green }
    else     { $script:fail++; Write-Host "FAIL  $name  $detail" -ForegroundColor Red }
}

function Call-Api([string]$Method, [string]$Url, [string]$Token, [string]$Body) {
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $p = @{ Uri = $Url; Method = $Method; Headers = $headers; UseBasicParsing = $true; ErrorAction = 'Stop' }
    if ($Body) { $p['Body'] = $Body; $p['ContentType'] = 'application/json' }
    $status = 0
    $content = $null
    try {
        $resp = Invoke-WebRequest @p
        $status = [int]$resp.StatusCode
        # Decode bytes ourselves: .Content can be byte[] for vendor JSON types (actuator)
        $content = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    } catch {
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    }
    $json = $null
    if ($content) { try { $json = $content | ConvertFrom-Json } catch { } }
    return [pscustomobject]@{ Status = $status; Json = $json }
}

# Login from api.ps1 returns auth data (headers or token): extract the raw JWT from it
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

function Get-JwtSub([string]$token) {
    $p = $token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($p.Length % 4) { 2 { $p += '==' } 3 { $p += '=' } }
    $json = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p))
    return ($json | ConvertFrom-Json).sub
}

function Unread([string]$token) {
    $r = Call-Api 'GET' "$Gateway/api/v1/notifications/unread-count" $token $null
    if ($r.Status -ne 200 -or $null -eq $r.Json) { return -1 }
    return [int]$r.Json.unread
}

function Wait-Until([scriptblock]$cond, [int]$timeoutSec = 25) {
    $end = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $end) {
        if (& $cond) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function New-Event([string]$eventId, [string]$status, $requesterId, [string]$number) {
    $details = [ordered]@{ requestNumber = $number; fromStatus = 'UNDER_REVIEW'; newStatus = $status }
    if ($requesterId) { $details['requesterId'] = $requesterId }
    $e = [ordered]@{
        eventId       = $eventId
        service       = 'request-service'
        action        = 'request.status_changed'
        entityType    = 'REQUEST'
        entityId      = $reqId
        actorId       = $null
        actorRole     = $null
        traceId       = "test-$runId"
        occurredAt    = $now
        schemaVersion = 1
        details       = $details
    }
    return ($e | ConvertTo-Json -Compress -Depth 5)
}

# ---------------------------------------------------------------- 0. Preparation
Write-Host "`n== 0. Preparation ==" -ForegroundColor Cyan

$runId = [guid]::NewGuid().ToString('N').Substring(0, 8)
$reqId = [guid]::NewGuid().ToString()
$now   = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')

$h = Call-Api 'GET' "$NotificationUrl/actuator/health" $null $null
Check 'notification-service health is UP (direct, no token)' ($h.Status -eq 200 -and $h.Json.status -eq 'UP') "status=$($h.Status)"

# Test users are created through admin (already existing users are fine)
$adminAuth = Login $AdminLogin $AdminPassword
Check 'admin login (api.ps1 Login)' ([bool](Get-Token $adminAuth)) 'check admin password'
foreach ($lg in @($TestLogin1, $TestLogin2)) {
    try {
        Post "$g/users" @{ login = $lg; password = $TestPassword; fullName = "Notif test $lg"; roles = @('USER') } $adminAuth | Out-Null
    } catch { }
}

$t1 = Get-Token (Login $TestLogin1 $TestPassword)
$t2 = Get-Token (Login $TestLogin2 $TestPassword)
Check "login $TestLogin1" ([bool]$t1) "user may exist with another password"
Check "login $TestLogin2" ([bool]$t2) "user may exist with another password"
if (-not $t1 -or -not $t2) { Write-Host "Cannot continue without tokens." -ForegroundColor Red; exit 1 }

$id1 = Get-JwtSub $t1
$id2 = Get-JwtSub $t2
$isGuid = [guid]::Empty
Check 'JWT sub is a UUID (service assumes this)' (([guid]::TryParse([string]$id1, [ref]$isGuid)) -and ([guid]::TryParse([string]$id2, [ref]$isGuid))) "sub1=$id1"

# ---------------------------------------------------------------- 1. Access control
Write-Host "`n== 1. Access control ==" -ForegroundColor Cyan

$r = Call-Api 'GET' "$Gateway/api/v1/notifications" $null $null
Check 'GET /notifications without token -> 401' ($r.Status -eq 401) "status=$($r.Status)"

$r = Call-Api 'GET' "$Gateway/api/v1/notifications" $t1 $null
Check 'GET /notifications with token -> 200' ($r.Status -eq 200) "status=$($r.Status)"

$n1_0 = Unread $t1
$n2_0 = Unread $t2
Check 'unread-count works for both users' (($n1_0 -ge 0) -and ($n2_0 -ge 0)) "u1=$n1_0 u2=$n2_0"

# ---------------------------------------------------------------- 2. Publish events
Write-Host "`n== 2. Publish events to Kafka ==" -ForegroundColor Cyan

if (-not $KafkaContainer) {
    $KafkaContainer = (docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -match 'kafka' } | Select-Object -First 1)
}
Check 'Kafka container found' ([bool]$KafkaContainer) 'use -KafkaContainer <name>'
if (-not $KafkaContainer) { exit 1 }

$producer = '/opt/kafka/bin/kafka-console-producer.sh'
docker exec $KafkaContainer test -x $producer 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { $producer = 'kafka-console-producer.sh' }

$e1 = [guid]::NewGuid().ToString()
$lines = @(
    (New-Event $e1                            'APPROVED'            $id1 "T$runId-1"),
    (New-Event ([guid]::NewGuid().ToString()) 'PARTIALLY_AVAILABLE' $id1 "T$runId-2"),
    (New-Event ([guid]::NewGuid().ToString()) 'RESERVED'            $id1 "T$runId-3"),
    (New-Event ([guid]::NewGuid().ToString()) 'RESERVED_PARTIAL'    $id1 "T$runId-4"),
    (New-Event ([guid]::NewGuid().ToString()) 'READY_FOR_ISSUE'     $id1 "T$runId-5"),
    (New-Event ([guid]::NewGuid().ToString()) 'ISSUED'              $id1 "T$runId-6"),
    # duplicate of the first event (same eventId): must NOT create a second notification
    (New-Event $e1                            'APPROVED'            $id1 "T$runId-1"),
    # status without a notification: must be ignored
    (New-Event ([guid]::NewGuid().ToString()) 'PICKING'             $id1 "T$runId-7"),
    # no requesterId: must be ignored
    (New-Event ([guid]::NewGuid().ToString()) 'RESERVED'            $null "T$runId-8"),
    # garbage: consumer must skip it and keep working
    'this-is-not-json',
    # event for the second user (sent last)
    (New-Event ([guid]::NewGuid().ToString()) 'RESERVED'            $id2 "T$runId-9")
)

$lines | & docker exec -i $KafkaContainer $producer --bootstrap-server $KafkaBootstrap --topic $Topic 2>&1 | Out-Null
Check 'events published to Kafka' ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE (check producer path and topic)"

# ---------------------------------------------------------------- 3. Consumption
Write-Host "`n== 3. Consumption ==" -ForegroundColor Cyan

$ok1 = Wait-Until { (Unread $t1) -ge ($n1_0 + 6) }
$ok2 = Wait-Until { (Unread $t2) -ge ($n2_0 + 1) }
Check "user1 received notifications (waited up to 25 s)" $ok1 "unread=$(Unread $t1), expected $($n1_0 + 6)"
Check "user2 received notification (garbage did not stop the consumer)" $ok2 "unread=$(Unread $t2), expected $($n2_0 + 1)"
Start-Sleep -Seconds 3   # let duplicate / ignored events be processed too

$u1 = Unread $t1
$u2 = Unread $t2
Check 'user1 got exactly 6 new (duplicate, PICKING and no-recipient ignored)' ($u1 -eq ($n1_0 + 6)) "got +$($u1 - $n1_0)"
Check 'user2 got exactly 1 new' ($u2 -eq ($n2_0 + 1)) "got +$($u2 - $n2_0)"

$list = Call-Api 'GET' "$Gateway/api/v1/notifications?size=100" $t1 $null
$mine = @()
if ($list.Json) { $mine = @($list.Json.content | Where-Object { $_.requestId -eq $reqId }) }
Check 'user1 list contains 6 notifications of this run' ($mine.Count -eq 6) "count=$($mine.Count)"

$expectedTypes = 'REQUEST_APPROVED,REQUEST_ISSUED,REQUEST_PARTIALLY_AVAILABLE,REQUEST_READY_FOR_ISSUE,REQUEST_RESERVED,REQUEST_RESERVED_PARTIAL'
$gotTypes = (($mine | ForEach-Object { $_.type } | Sort-Object) -join ',')
Check 'notification types match statuses' ($gotTypes -eq $expectedTypes) "got=$gotTypes"

$list2 = Call-Api 'GET' "$Gateway/api/v1/notifications?size=100" $t2 $null
$foreign = @()
if ($list2.Json) { $foreign = @($list2.Json.content | Where-Object { $_.requestId -eq $reqId }) }
Check 'user2 sees only own notification (1), not user1 ones' ($foreign.Count -eq 1) "count=$($foreign.Count)"

# ---------------------------------------------------------------- 4. Read marks
Write-Host "`n== 4. Read marks ==" -ForegroundColor Cyan

if ($mine.Count -ge 1) {
    $target = $mine[0]
    $r = Call-Api 'POST' "$Gateway/api/v1/notifications/$($target.id)/read" $t2 $null
    Check "foreign user cannot mark someone else's notification (404)" ($r.Status -eq 404) "status=$($r.Status)"

    $r = Call-Api 'POST' "$Gateway/api/v1/notifications/$([guid]::NewGuid())/read" $t1 $null
    Check 'unknown notification id -> 404' ($r.Status -eq 404) "status=$($r.Status)"

    $r = Call-Api 'POST' "$Gateway/api/v1/notifications/$($target.id)/read" $t1 $null
    Check 'owner marks notification as read (200, read=true)' ($r.Status -eq 200 -and $r.Json.read -eq $true) "status=$($r.Status)"

    $r = Call-Api 'POST' "$Gateway/api/v1/notifications/$($target.id)/read" $t1 $null
    Check 'repeat mark-read is idempotent (200)' ($r.Status -eq 200) "status=$($r.Status)"

    Check 'unread-count decreased by 1' ((Unread $t1) -eq ($u1 - 1)) "unread=$(Unread $t1), expected $($u1 - 1)"

    $r = Call-Api 'GET' "$Gateway/api/v1/notifications?unreadOnly=true&size=100" $t1 $null
    $stillThere = @($r.Json.content | Where-Object { $_.id -eq $target.id })
    Check 'unreadOnly=true does not return the read notification' ($stillThere.Count -eq 0)
} else {
    Check 'read marks (skipped: no notifications)' $false
}

# ---------------------------------------------------------------- 5. Paging
Write-Host "`n== 5. Paging ==" -ForegroundColor Cyan

$r = Call-Api 'GET' "$Gateway/api/v1/notifications?size=2&page=0" $t1 $null
Check 'size=2 returns 2 items' ($r.Status -eq 200 -and @($r.Json.content).Count -eq 2 -and $r.Json.size -eq 2) "status=$($r.Status)"
Check 'totalElements >= 6' ($r.Json.totalElements -ge 6) "total=$($r.Json.totalElements)"

$r = Call-Api 'GET' "$Gateway/api/v1/notifications?size=1000" $t1 $null
Check 'size=1000 is clamped to 100' ($r.Status -eq 200 -and $r.Json.size -eq 100) "size=$($r.Json.size)"

# ---------------------------------------------------------------- 6. Read all
Write-Host "`n== 6. Read all ==" -ForegroundColor Cyan

$r = Call-Api 'POST' "$Gateway/api/v1/notifications/read-all" $t1 $null
Check 'read-all -> 200' ($r.Status -eq 200) "status=$($r.Status)"
Check 'user1 unread-count is 0 after read-all' ((Unread $t1) -eq 0) "unread=$(Unread $t1)"
Check 'read-all does not touch other users (user2 unchanged)' ((Unread $t2) -eq $u2) "unread=$(Unread $t2), expected $u2"

# ---------------------------------------------------------------- Summary
Write-Host ""
$color = 'Green'
if ($script:fail -gt 0) { $color = 'Red' }
Write-Host "Result: PASS=$($script:pass)  FAIL=$($script:fail)" -ForegroundColor $color
if ($script:fail -gt 0) { exit 1 }

