if (-not $env:WMS_ADMIN_PASSWORD) { throw "Run: . .\scripts\dev-env.ps1" }
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\api.ps1"

$script:failed = 0
function Step($t) { Write-Host ""; Write-Host "=== $t" -ForegroundColor Cyan }
function Check($name, $ok) {
  if ($ok) { Write-Host "  PASS  $name" -ForegroundColor Green }
  else { Write-Host "  FAIL  $name" -ForegroundColor Red; $script:failed++ }
}
function Need($value, $what) {
  if ($null -eq $value -or "$value" -eq "") { throw "Missing: $what. Fix this first, later steps depend on it." }
  $value
}
function StatusOf([scriptblock]$action) {
  try { & $action | Out-Null; 0 } catch { [int]$_.Exception.Response.StatusCode }
}
function Summary {
  Write-Host ""
  if ($script:failed -eq 0) { Write-Host "ALL CHECKS PASSED" -ForegroundColor Green }
  else { Write-Host "$($script:failed) CHECK(S) FAILED" -ForegroundColor Red }
}

# ---- accounts and reference data ----
$ha = Login "admin" $env:WMS_ADMIN_PASSWORD
$hs = Login "store1" "Store-12345"
foreach ($u in @("user1", "user2")) {
  try { Post "$g/users" @{ login=$u; password="User-12345"; fullName="Test $u"; roles=@("USER") } $ha | Out-Null } catch { }
}
foreach ($u in @("worker1", "worker2")) {
  try { Post "$g/users" @{ login=$u; password="Worker-12345"; fullName="Test $u"; roles=@("WAREHOUSE_WORKER") } $ha | Out-Null } catch { }
}
$hu = Login "user1" "User-12345"; $hu2 = Login "user2" "User-12345"
$hw = Login "worker1" "Worker-12345"; $hw2 = Login "worker2" "Worker-12345"
$w1 = (GetApi "$g/me" $hw).id; $w2 = (GetApi "$g/me" $hw2).id; $u1 = (GetApi "$g/me" $hu).id
$wh = Need (GetApi "$g/warehouses" $hs | Where-Object { $_.code -eq "WH1" }) "warehouse WH1"
$prod = Need ((GetApi "$g/products?q=ARM-2000-T" $hs).content | Select-Object -First 1) "product ARM-2000-T"

function Available {
  @(Post "$g/reservations/availability" @{ warehouseId=$wh.id; items=@(@{ productId=$prod.id; quantity=1 }) } $hs)[0].available
}
function Add-Stock([int]$qty) {
  Step "Receive $qty pcs into stock"
  $rc = Post "$g/receipts" @{ warehouseId=$wh.id; supplierName="Test supplier"; items=@(@{ productId=$prod.id; quantity=$qty }) } $hs
  $rec = Post "$g/receipts/$($rc.id)/recommend-placement" @{} $hs
  Check "placement recommended (ASSIGNED)" (@($rec)[0].status -eq "ASSIGNED")
  Post "$g/receipts/$($rc.id)/confirm" @{} $hs | Out-Null
  $task = Post "$g/receipts/$($rc.id)/placement-tasks" @{} $hs
  foreach ($it in $task.items) {
    Post "$g/placement-tasks/$($task.id)/items/$($it.itemId)/confirm" @{ sectionId=$it.targetSectionId; quantity=$it.quantity } $hw | Out-Null
  }
  Check "receipt DONE" ((GetApi "$g/receipts/$($rc.id)" $hs).status -eq "DONE")
}
function Ensure-Stock([int]$minimum) { if ((Available) -lt $minimum) { Add-Stock ($minimum + 20) } }

# a request that goes all the way to RESERVED
function New-ReservedRequest([int]$qty) {
  $rq = Post "$g/requests" @{ warehouseId=$wh.id; items=@(@{ productId=$prod.id; quantity=$qty }) } $hu
  Post "$g/requests/$($rq.id)/submit" @{} $hu | Out-Null
  Post "$g/requests/$($rq.id)/review" @{} $hs | Out-Null
  Post "$g/requests/$($rq.id)/reserve" @{} $hs
}