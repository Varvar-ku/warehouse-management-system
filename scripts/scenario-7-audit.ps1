# scenario-7-audit.ps1
# Smoke test for audit-service (through api-gateway). PowerShell 5.1, ASCII only.
#
# Usage (from repo root):
#   .\scripts\scenario-7-audit.ps1
#   .\scripts\scenario-7-audit.ps1 -UserPassword "<user1 dev password>"
#   .\scripts\scenario-7-audit.ps1 -PgContainer "<postgres container name>"
#
# Part A (always): access control, response shape, filters, input validation.
# Part B (needs docker + a running postgres container): end-to-end
#   outbox_event (inventory_db) -> Kafka -> audit-service -> API, deduplication,
#   prefix filter and immutability of audit_log.
# Test rows have entityType SMOKETEST. audit_log is append-only, so they stay.

param(
    [string]$Gateway       = "http://localhost:8080",
    [string]$LoginPath     = "/api/v1/auth/login",
    [string]$LoginField    = "login",
    [string]$AdminLogin    = "admin",
    [string]$AdminPassword = $env:WMS_ADMIN_PASSWORD,
    [string]$UserLogin     = "user1",
    [string]$UserPassword  = "",
    [string]$PgContainer   = "",
    [string]$DbUser        = "warehouse",
    [string]$OutboxDb      = "inventory_db",
    [string]$AuditDb       = "audit_db",
    [int]$TimeoutSec       = 20
)

$ErrorActionPreference = "Continue"
$script:pass = 0
$script:fail = 0
$script:skip = 0

function Write-Pass($m) { $script:pass++; Write-Host ("PASS  " + $m) -ForegroundColor Green }
function Write-Fail($m) { $script:fail++; Write-Host ("FAIL  " + $m) -ForegroundColor Red }
function Write-Skip($m) { $script:skip++; Write-Host ("SKIP  " + $m) -ForegroundColor Yellow }
function Test-Check($cond, $m) { if ($cond) { Write-Pass $m } else { Write-Fail $m } }

function Invoke-Api {
    param([string]$Method, [string]$Url, [string]$Token, $Body)
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer " + $Token }
    $params = @{ Method = $Method; Uri = $Url; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $params["Body"] = ($Body | ConvertTo-Json -Depth 5 -Compress)
        $params["ContentType"] = "application/json"
    }
    $status = 0
    $content = ""
    try {
        $r = Invoke-WebRequest @params
        $status = [int]$r.StatusCode
        $content = $r.Content
    } catch {
        $resp = $_.Exception.Response
        if ($null -ne $resp) {
            $status = [int]$resp.StatusCode
            try {
                $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $content = $sr.ReadToEnd()
            } catch { }
        }
    }
    $json = $null
    if ($content) { try { $json = $content | ConvertFrom-Json } catch { } }
    return [pscustomobject]@{ Status = $status; Content = $content; Json = $json }
}

function Get-Token($login, $password) {
    $body = @{}
    $body[$LoginField] = $login
    $body["password"] = $password
    $r = Invoke-Api -Method POST -Url ($Gateway + $LoginPath) -Body $body
    if ($r.Status -ne 200 -or $null -eq $r.Json) { return $null }
    foreach ($n in @("accessToken", "token", "access_token", "jwt")) {
        if ($r.Json.PSObject.Properties[$n]) { return [string]$r.Json.$n }
    }
    return $null
}

function Invoke-Psql($db, $sql) {
    $out = $sql | docker exec -i $script:pg psql -U $DbUser -d $db -v ON_ERROR_STOP=1 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $out }
}

$base = $Gateway + "/api/v1/audit/events"

Write-Host ""
Write-Host "=== Part A: access, shape, filters ===" -ForegroundColor Cyan

# A1. login as admin
$adminToken = Get-Token $AdminLogin $AdminPassword
if (-not $adminToken) {
    Write-Fail ("admin login failed (check -LoginPath '" + $LoginPath + "', -LoginField '" + $LoginField + "', -AdminPassword)")
    Write-Host "Cannot continue without an admin token." -ForegroundColor Red
    exit 1
}
Write-Pass "admin login"

# A2. no token -> 401
$r = Invoke-Api GET $base
Test-Check ($r.Status -eq 401) ("no token -> 401 (got " + $r.Status + ")")

# A3. USER -> 403
if ($UserPassword) {
    $userToken = Get-Token $UserLogin $UserPassword
    if ($userToken) {
        $r = Invoke-Api GET $base $userToken
        Test-Check ($r.Status -eq 403) ("USER token -> 403 (got " + $r.Status + ")")
    } else {
        Write-Fail ("login as " + $UserLogin + " failed")
    }
} else {
    Write-Skip "USER -> 403 (pass -UserPassword to run)"
}

# A4. admin -> 200 and page shape
$r = Invoke-Api GET $base $adminToken
Test-Check ($r.Status -eq 200) ("admin GET list -> 200 (got " + $r.Status + ")")
if ($r.Status -eq 200 -and $null -ne $r.Json) {
    $names = @($r.Json.PSObject.Properties.Name)
    $missing = @("items", "page", "size", "totalItems", "totalPages") | Where-Object { $names -notcontains $_ }
    Test-Check ($missing.Count -eq 0) ("page has items/page/size/totalItems/totalPages (missing: " + ($missing -join ",") + ")")
    Write-Host ("      events in audit_log now: " + $r.Json.totalItems)
}

# A5. page size is capped
$r = Invoke-Api GET ($base + "?size=1000") $adminToken
Test-Check ($r.Status -eq 200 -and $r.Json.size -le 200) ("size=1000 is capped (size=" + $r.Json.size + ")")

# A6. filter with no matches
$r = Invoke-Api GET ($base + "?entityType=NO_SUCH_TYPE_XYZ") $adminToken
Test-Check ($r.Status -eq 200 -and $r.Json.totalItems -eq 0) "unknown entityType -> empty result"

# A7. date filter in the far future
$r = Invoke-Api GET ($base + "?from=2999-01-01T00:00:00Z") $adminToken
Test-Check ($r.Status -eq 200 -and $r.Json.totalItems -eq 0) "from=2999 -> empty result"

# A8. bad actorId -> 400
$r = Invoke-Api GET ($base + "?actorId=not-a-uuid") $adminToken
Test-Check ($r.Status -eq 400) ("actorId=not-a-uuid -> 400 (got " + $r.Status + ")")

Write-Host ""
Write-Host "=== Part B: outbox -> Kafka -> audit (end to end) ===" -ForegroundColor Cyan

# find postgres container
$script:pg = $PgContainer
if (-not $script:pg) {
    $lines = docker ps --format "{{.Names}}|{{.Image}}" 2>$null
    foreach ($l in @($lines)) {
        if ($l -match "postgres") { $script:pg = ($l -split "\|")[0]; break }
    }
}

if (-not $script:pg) {
    Write-Skip "Part B skipped: no postgres container found (use -PgContainer <name>; see 'docker ps')"
} else {
    Write-Host ("      using container: " + $script:pg)
    $runId = [guid]::NewGuid().ToString("N")
    $eventId = [guid]::NewGuid().ToString()
    $nowIso = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    $payload = @{
        eventId       = $eventId
        schemaVersion = 1
        service       = "smoke-test"
        action        = "smoke.test_event"
        entityType    = "SMOKETEST"
        entityId      = $runId
        actorId       = $null
        actorRole     = $null
        occurredAt    = $nowIso
        traceId       = "smoke-" + $runId
        details       = @{ note = "e2e" }
    } | ConvertTo-Json -Compress -Depth 5

    # two outbox rows with the SAME eventId inside the payload -> audit must keep one
    $o1 = [guid]::NewGuid().ToString()
    $o2 = [guid]::NewGuid().ToString()
    $key = "SMOKETEST:" + $runId
    $sql = "INSERT INTO outbox_event (event_id, topic, event_key, payload) VALUES " +
           "('" + $o1 + "', 'wms.audit.v1', '" + $key + "', '" + $payload + "'::jsonb), " +
           "('" + $o2 + "', 'wms.audit.v1', '" + $key + "', '" + $payload + "'::jsonb);"
    $res = Invoke-Psql $OutboxDb $sql
    if ($res.ExitCode -ne 0) {
        Write-Fail ("insert into outbox_event failed: " + $res.Output.Trim())
    } else {
        Write-Pass "two outbox rows inserted (same payload eventId)"

        # wait for relay -> Kafka -> consumer
        $q = $base + "?entityType=SMOKETEST&entityId=" + $runId
        $deadline = (Get-Date).AddSeconds($TimeoutSec)
        $found = $null
        while ((Get-Date) -lt $deadline) {
            $r = Invoke-Api GET $q $adminToken
            if ($r.Status -eq 200 -and $r.Json.totalItems -ge 1) { $found = $r; break }
            Start-Sleep -Milliseconds 1000
        }

        if ($null -eq $found) {
            Write-Fail ("event did not reach audit within " + $TimeoutSec + "s. Check: inventory-service log (relay), Kafka, audit-service log; SELECT id, published_at FROM outbox_event ORDER BY id DESC;")
        } else {
            Write-Pass "event arrived in audit"
            $ev = $found.Json.items[0]
            Test-Check ($ev.eventId -eq $eventId) "eventId preserved"
            Test-Check ($ev.action -eq "smoke.test_event") "action preserved"
            Test-Check ($ev.service -eq "smoke-test") "service preserved"
            Test-Check ($ev.entityType -eq "SMOKETEST" -and $ev.entityId -eq $runId) "entity preserved"
            Test-Check ($ev.traceId -eq ("smoke-" + $runId)) "traceId preserved"
            Test-Check ($null -ne $ev.details -and $ev.details.note -eq "e2e") "details preserved"

            # deduplication: give the duplicate time to be consumed
            Start-Sleep -Seconds 4
            $r = Invoke-Api GET $q $adminToken
            Test-Check ($r.Json.totalItems -eq 1) ("duplicate eventId stored once (count=" + $r.Json.totalItems + ")")

            # prefix filter
            $r = Invoke-Api GET ($base + "?action=smoke.*&entityId=" + $runId) $adminToken
            Test-Check ($r.Status -eq 200 -and $r.Json.totalItems -eq 1) "action prefix filter smoke.* works"

            # filter by time window around the event
            $r = Invoke-Api GET ($base + "?entityId=" + $runId + "&from=2000-01-01T00:00:00Z&to=2999-01-01T00:00:00Z") $adminToken
            Test-Check ($r.Status -eq 200 -and $r.Json.totalItems -eq 1) "from/to window filter works"

            # immutability (triggers). Scope is limited to this test row.
            $upd = Invoke-Psql $AuditDb ("UPDATE audit_log SET action = 'tampered' WHERE entity_id = '" + $runId + "';")
            Test-Check ($upd.ExitCode -ne 0 -and $upd.Output -match "immutable") "UPDATE on audit_log is rejected"
            $del = Invoke-Psql $AuditDb ("DELETE FROM audit_log WHERE entity_id = '" + $runId + "';")
            Test-Check ($del.ExitCode -ne 0 -and $del.Output -match "immutable") "DELETE on audit_log is rejected"

            $r = Invoke-Api GET $q $adminToken
            Test-Check ($r.Json.items[0].action -eq "smoke.test_event") "row unchanged after tamper attempts"
        }
    }
}

Write-Host ""
Write-Host ("RESULT: " + $script:pass + " passed, " + $script:fail + " failed, " + $script:skip + " skipped")
if ($script:fail -gt 0) { exit 1 } else { exit 0 }
