# scenario-10-stock-report.ps1
# Checks GET /api/v1/reports/stock (report-service) against GET /api/v1/stock (inventory-service).
# ASCII only (PowerShell 5.1). Run from the repo root:  .\scripts\scenario-10-stock-report.ps1
# Prerequisite: some stock exists (run scenario-5b.ps1 first) and movement events were already consumed.
# Passwords: parameters or env WMS_ADMIN_PASSWORD / WMS_STORE_PASSWORD / WMS_USER_PASSWORD, otherwise prompted.
param(
    [string]$Base = "http://localhost:8080",
    [string]$AdminLogin = "admin",
    [string]$StoreLogin = "store1",
    [string]$UserLogin = "user1",
    [string]$AdminPassword = $env:WMS_ADMIN_PASSWORD,
    [string]$StorePassword = $env:WMS_STORE_PASSWORD,
    [string]$UserPassword = $env:WMS_USER_PASSWORD,
    [int]$WaitSeconds = 30
)

$ErrorActionPreference = "Stop"
$script:pass = 0
$script:fail = 0

function Check([string]$name, [bool]$ok, [string]$extra = "") {
    if ($ok) { Write-Host "PASS  $name"; $script:pass++ }
    else { Write-Host "FAIL  $name  $extra"; $script:fail++ }
}

function Ask([string]$value, [string]$prompt) {
    if ($value) { return $value }
    $sec = Read-Host -Prompt $prompt -AsSecureString
    return [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
}

# Returns @{ Status = <int>; Body = <object or $null> }
function Call([string]$path, [string]$token = "") {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    try {
        $r = Invoke-WebRequest -Uri ($Base + $path) -Headers $headers -UseBasicParsing -Method Get
        $body = $null
        if ($r.Content) { $body = $r.Content | ConvertFrom-Json }
        return @{ Status = [int]$r.StatusCode; Body = $body }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { throw }
        return @{ Status = [int]$resp.StatusCode; Body = $null }
    }
}

function Login([string]$login, [string]$password) {
    $json = @{ login = $login; password = $password } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$Base/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $json
    foreach ($n in @("accessToken", "token", "access_token")) {
        if ($r.PSObject.Properties.Name -contains $n) { return $r.$n }
    }
    throw "No token field in login response for $login"
}

function Esc([string]$s) { return [uri]::EscapeDataString($s) }

# All pages of a paged endpoint (inventory: size max 100; report: up to 500)
function AllRows([string]$path, [string]$token, [int]$size) {
    $rows = @()
    $page = 0
    while ($true) {
        $sep = "?"
        if ($path.Contains("?")) { $sep = "&" }
        $r = Call "$path${sep}page=$page&size=$size" $token
        if ($r.Status -ne 200) { throw "GET $path returned $($r.Status)" }
        $rows += @($r.Body.content)
        $page++
        if ($page -ge [int]$r.Body.totalPages) { break }
    }
    return $rows
}

function Key($row) { return "$($row.sectionId)|$($row.batchId)" }

Write-Host ""
Write-Host "== 0. Preparation =="
$AdminPassword = Ask $AdminPassword "admin password"
$StorePassword = Ask $StorePassword "$StoreLogin password"
$UserPassword = Ask $UserPassword "$UserLogin password"
$adminToken = Login $AdminLogin $AdminPassword
$storeToken = Login $StoreLogin $StorePassword
$userToken = Login $UserLogin $UserPassword
Check "logins (admin, storekeeper, user)" ($adminToken -and $storeToken -and $userToken)

Write-Host ""
Write-Host "== 1. Access control =="
$r = Call "/api/v1/reports/stock"
Check "no token -> 401" ($r.Status -eq 401) "status=$($r.Status)"
$r = Call "/api/v1/reports/stock" $userToken
Check "USER -> 403" ($r.Status -eq 403) "status=$($r.Status)"
$r = Call "/api/v1/reports/stock" $storeToken
Check "STOREKEEPER -> 200 (route exists in gateway)" ($r.Status -eq 200) "status=$($r.Status); if 404 add /api/v1/reports/stock to the gateway"
$r = Call "/api/v1/reports/stock" $adminToken
Check "ADMIN -> 200" ($r.Status -eq 200) "status=$($r.Status)"

Write-Host ""
Write-Host "== 2. Reconciliation with inventory =="
$inv = @(AllRows "/api/v1/stock" $storeToken 100)
Check "inventory has stock rows (run scenario-5b first if not)" ($inv.Count -gt 0) "count=$($inv.Count)"

# Report is fed through Kafka: wait until the number of rows matches or time is up
$deadline = (Get-Date).AddSeconds($WaitSeconds)
$rep = @()
do {
    $rep = @(AllRows "/api/v1/reports/stock" $storeToken 500)
    if ($rep.Count -eq $inv.Count) { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

$repMap = @{}
foreach ($x in $rep) { $repMap[(Key $x)] = [decimal]$x.quantity }
$invMap = @{}
foreach ($x in $inv) { $invMap[(Key $x)] = [decimal]$x.quantity }

Check "same number of nonzero rows" ($rep.Count -eq $inv.Count) "inventory=$($inv.Count) report=$($rep.Count)"
$bad = @()
foreach ($k in $invMap.Keys) {
    if (-not $repMap.ContainsKey($k)) { $bad += "missing in report: $k" }
    elseif ($repMap[$k] -ne $invMap[$k]) { $bad += "qty differs $k inventory=$($invMap[$k]) report=$($repMap[$k])" }
}
foreach ($k in $repMap.Keys) {
    if (-not $invMap.ContainsKey($k)) { $bad += "extra in report: $k qty=$($repMap[$k])" }
}
Check "every row matches by section+batch and quantity" ($bad.Count -eq 0) ($bad | Select-Object -First 5 | Out-String)
$neg = @($rep | Where-Object { [decimal]$_.quantity -lt 0 })
Check "no negative balances in report" ($neg.Count -eq 0) "count=$($neg.Count)"

Write-Host ""
Write-Host "== 3. Filters =="
if ($rep.Count -gt 0) {
    $s = $rep[0]
    $q = "warehouseId=$($s.warehouseId)"
    $f = @(AllRows "/api/v1/reports/stock?$q" $storeToken 500)
    Check "warehouseId filter" ($f.Count -gt 0 -and @($f | Where-Object { $_.warehouseId -ne $s.warehouseId }).Count -eq 0)
    $f = @(AllRows "/api/v1/reports/stock?sectionId=$($s.sectionId)" $storeToken 500)
    Check "sectionId filter" ($f.Count -gt 0 -and @($f | Where-Object { $_.sectionId -ne $s.sectionId }).Count -eq 0)
    $f = @(AllRows "/api/v1/reports/stock?productId=$($s.productId)" $storeToken 500)
    Check "productId filter" ($f.Count -gt 0 -and @($f | Where-Object { $_.productId -ne $s.productId }).Count -eq 0)
    $f = @(AllRows "/api/v1/reports/stock?sku=$(Esc $s.productSku)" $storeToken 500)
    Check "sku filter (substring, case-insensitive)" (@($f | Where-Object { (Key $_) -eq (Key $s) }).Count -eq 1)
    if ($s.batchNumber) {
        $f = @(AllRows "/api/v1/reports/stock?batchNumber=$(Esc $s.batchNumber)" $storeToken 500)
        Check "batchNumber filter" (@($f | Where-Object { $_.batchNumber -ne $s.batchNumber }).Count -eq 0 -and $f.Count -gt 0)
    }
    $f = @(AllRows "/api/v1/reports/stock?warehouseId=00000000-0000-0000-0000-000000000000" $storeToken 500)
    Check "unknown warehouse -> empty" ($f.Count -eq 0)
} else {
    Check "filters (skipped: report is empty)" $false
}

Write-Host ""
Write-Host "== 4. includeZero and asOf =="
$withZero = @(AllRows "/api/v1/reports/stock?includeZero=true" $storeToken 500)
Check "includeZero=true returns >= rows" ($withZero.Count -ge $rep.Count) "zero=$($withZero.Count) base=$($rep.Count)"
$r = Call "/api/v1/reports/stock?asOf=2000-01-01" $storeToken
Check "asOf=2000-01-01 -> no rows" ($r.Status -eq 200 -and [int]$r.Body.totalElements -eq 0) "status=$($r.Status)"
$today = (Get-Date).ToString("yyyy-MM-dd")
$r = Call "/api/v1/reports/stock?asOf=$today&size=1" $storeToken
Check "asOf=today equals current total" ($r.Status -eq 200 -and [int]$r.Body.totalElements -eq $rep.Count) "status=$($r.Status) total=$($r.Body.totalElements) expected=$($rep.Count)"
$tomorrow = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$r = Call "/api/v1/reports/stock?asOf=$tomorrow&size=1" $storeToken
Check "asOf=tomorrow equals current total" ($r.Status -eq 200 -and [int]$r.Body.totalElements -eq $rep.Count)
$r = Call "/api/v1/reports/stock?asOf=not-a-date" $storeToken
Check "asOf=not-a-date -> 400" ($r.Status -eq 400) "status=$($r.Status)"

Write-Host ""
Write-Host "== 5. Paging =="
if ($rep.Count -ge 2) {
    $r = Call "/api/v1/reports/stock?size=1&page=0" $storeToken
    Check "size=1 returns 1 item" (@($r.Body.content).Count -eq 1 -and [int]$r.Body.totalPages -eq $rep.Count) "pages=$($r.Body.totalPages)"
    $r2 = Call "/api/v1/reports/stock?size=1&page=1" $storeToken
    Check "page=1 returns a different row" ((Key $r.Body.content[0]) -ne (Key $r2.Body.content[0]))
}
$r = Call "/api/v1/reports/stock?size=100000" $storeToken
Check "size=100000 is clamped to 500" ($r.Status -eq 200 -and [int]$r.Body.size -eq 500) "size=$($r.Body.size)"

Write-Host ""
Write-Host "Result: PASS=$($script:pass)  FAIL=$($script:fail)"
if ($script:fail -gt 0) { exit 1 }
