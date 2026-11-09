# Checks login brute-force protection (auth-service, table login_throttle).
# Run from repo root after:  . .\scripts\dev-env.ps1
#   .\scripts\scenario-11-login-throttle.ps1            (waits ~30 s for a lock to expire)
#   .\scripts\scenario-11-login-throttle.ps1 -SkipWait  (skips the waiting part)
# Needs account user2 / User-12345 (created by helpers.ps1) and WMS_ADMIN_PASSWORD.
# The script clears login_throttle in auth_db at the start and at the end (dev only). ASCII only.
param(
    [string]$Gateway = 'http://localhost:8080',
    [string]$Container = 'warehouse-postgres',
    [string]$KnownLogin = 'user2',
    [string]$KnownPassword = 'User-12345',
    [switch]$SkipWait
)

$script:failed = 0
function Check([string]$name, [bool]$ok, [string]$hint = '') {
    if ($ok) { Write-Host "  PASS  $name" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  FAIL  $name  $hint" -ForegroundColor Red }
}

function TryLogin([string]$login, [string]$password) {
    $body = @{ login = $login; password = $password } | ConvertTo-Json
    try {
        $r = Invoke-WebRequest -Uri "$Gateway/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $body -UseBasicParsing
        return @{ Status = [int]$r.StatusCode; Body = [string]$r.Content; RetryAfter = $null }
        } catch {
            $resp = $_.Exception.Response
            $status = 0; $retry = $null; $text = [string]$_.Exception.Message
            if ($resp) {
                $status = [int]$resp.StatusCode
                $retry = $resp.Headers['Retry-After']
                $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8)
                $text = $reader.ReadToEnd()
                $reader.Close()
            }
            return @{ Status = $status; Body = $text; RetryAfter = $retry }
        }
}

function ClearThrottle {
    "DELETE FROM login_throttle;" | docker exec -i $Container psql -U warehouse -d auth_db | Out-Null
}

Write-Host ""
Write-Host "=== Setup"
ClearThrottle
$probe = 'throttle-probe-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
Write-Host "  unknown login for the test: $probe"

Write-Host ""
Write-Host "=== 1. Unknown login: 5 wrong attempts, then lock"
$codes = @()
for ($i = 1; $i -le 5; $i++) { $codes += (TryLogin $probe 'wrong-password-1').Status }
Check 'first 5 attempts return 401' (($codes | Where-Object { $_ -ne 401 }).Count -eq 0) ("got: " + ($codes -join ','))
$r6 = TryLogin $probe 'wrong-password-1'
Check '6th attempt returns 429' ($r6.Status -eq 429) ("got: " + $r6.Status)
Check 'code LOGIN_THROTTLED' ($r6.Body -match 'LOGIN_THROTTLED')
$ra = 0
[void][int]::TryParse([string]$r6.RetryAfter, [ref]$ra)
Check 'Retry-After header is a positive number' ($ra -gt 0) ("got: '" + $r6.RetryAfter + "'")

Write-Host ""
Write-Host "=== 2. Existing login behaves the same"
for ($i = 1; $i -le 5; $i++) { [void](TryLogin $KnownLogin 'wrong-password-2') }
$rKnown = TryLogin $KnownLogin $KnownPassword
Check 'correct password during lock still returns 429' ($rKnown.Status -eq 429) ("got: " + $rKnown.Status)
Check 'same error code as for unknown login' ($rKnown.Body -match 'LOGIN_THROTTLED')

Write-Host ""
Write-Host "=== 3. Other logins from the same IP are not blocked"
if ($env:WMS_ADMIN_PASSWORD) {
    $rAdmin = TryLogin 'admin' $env:WMS_ADMIN_PASSWORD
    Check 'admin logs in while other pairs are locked' ($rAdmin.Status -eq 200) ("got: " + $rAdmin.Status)
} else {
    Write-Host "  SKIP  WMS_ADMIN_PASSWORD is not set (run . .\scripts\dev-env.ps1)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== 4. Lock expires, successful login resets the counter"
if ($SkipWait) {
    Write-Host "  SKIP  -SkipWait" -ForegroundColor Yellow
} else {
    $wait = 0
    [void][int]::TryParse([string]$rKnown.RetryAfter, [ref]$wait)
    if ($wait -le 0) { $wait = 31 }
    Write-Host "  waiting $($wait + 1) s..."
    Start-Sleep -Seconds ($wait + 1)
    $rAfter = TryLogin $KnownLogin $KnownPassword
    Check 'correct password works after the lock expires' ($rAfter.Status -eq 200) ("got: " + $rAfter.Status + " (is the password of $KnownLogin correct?)")
    $rWrong = TryLogin $KnownLogin 'wrong-password-3'
    Check 'counter was reset: one wrong attempt gives 401, not 429' ($rWrong.Status -eq 401) ("got: " + $rWrong.Status)
}

Write-Host ""
ClearThrottle
if ($script:failed -eq 0) { Write-Host "ALL CHECKS PASSED" -ForegroundColor Green }
else { Write-Host "$($script:failed) CHECK(S) FAILED" -ForegroundColor Red }
