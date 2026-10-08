$ErrorActionPreference = "Stop"
. "$PSScriptRoot\api.ps1"

$script:failed = 0
function Step($t) { Write-Host ""; Write-Host "=== $t" -ForegroundColor Cyan }
function Check($name, $ok) {
  if ($ok) { Write-Host "  PASS  $name" -ForegroundColor Green }
  else { Write-Host "  FAIL  $name" -ForegroundColor Red; $script:failed++ }
}
# Stops the script if a value is empty: later steps depend on it
function Need($value, $what) {
  if ($null -eq $value -or "$value" -eq "") { throw "Missing: $what. Fix this first, later steps depend on it." }
  $value
}
# HTTP status of a failing call, or 0 if the call succeeded
function StatusOf([scriptblock]$action) {
  try { & $action | Out-Null; 0 } catch { [int]$_.Exception.Response.StatusCode }
}

# ---------------- setup ----------------
Step "Setup"
$ha = Login "admin" "Admin-2026-dev"
$hs = Login "store1" "Store-12345"
$hw = Login "worker1" "Worker-12345"
foreach ($u in @("user1", "user2")) {
  try { Post "$g/users" @{ login=$u; password="User-12345"; fullName="Test $u"; roles=@("USER") } $ha | Out-Null } catch { }
}
$hu = Login "user1" "User-12345"
$hu2 = Login "user2" "User-12345"
$wh = Need (GetApi "$g/warehouses" $hs | Where-Object { $_.code -eq "WH1" }) "warehouse WH1"
$prod = Need ((GetApi "$g/products?q=ARM-2000-T" $hs).content | Select-Object -First 1) "product ARM-2000-T"
Write-Host "  warehouse: $($wh.code), product: $($prod.sku)"

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

$av = Available
Write-Host "  available now: $av"
if ($av -lt 30) {
  Add-Stock 50
  $av = Available
  Write-Host "  available after receipt: $av"
}
Need $av "available stock" | Out-Null

# ---------------- 1. create ----------------
Step "1. Create request (user1)"
Check "fractional pcs rejected (400)" ((StatusOf { Post "$g/requests" @{ warehouseId=$wh.id; items=@(@{ productId=$prod.id; quantity=1.5 }) } $hu }) -eq 400)
$rq = Post "$g/requests" @{ warehouseId=$wh.id; priority=1; comment="For site 3"; items=@(@{ productId=$prod.id; quantity=20; comment="urgent" }) } $hu
Need $rq.id "id of the created request" | Out-Null
Check "status DRAFT" ($rq.status -eq "DRAFT")
Write-Host "  number: $($rq.number)"

# ---------------- 2. visibility and roles ----------------
Step "2. Visibility and roles"
Check "other user gets 404" ((StatusOf { GetApi "$g/requests/$($rq.id)" $hu2 }) -eq 404)
Check "user cannot review (403)" ((StatusOf { Post "$g/requests/$($rq.id)/review" @{} $hu }) -eq 403)
Check "storekeeper sees the request" (@((GetApi "$g/requests" $hs).content | Where-Object { $_.id -eq $rq.id }).Count -eq 1)
Check "admin has no access (403)" ((StatusOf { GetApi "$g/requests" $ha }) -eq 403)

# ---------------- 3. edit and submit ----------------
Step "3. Edit and submit"
Check "priority updated" ((Patch "$g/requests/$($rq.id)" @{ priority=2 } $hu).priority -eq 2)
Check "submitted" ((Post "$g/requests/$($rq.id)/submit" @{} $hu).status -eq "SUBMITTED")
Check "second submit rejected (409)" ((StatusOf { Post "$g/requests/$($rq.id)/submit" @{} $hu }) -eq 409)
Check "edit after submit rejected (409)" ((StatusOf { Patch "$g/requests/$($rq.id)" @{ priority=3 } $hu }) -eq 409)

# ---------------- 4. availability check ----------------
Step "4. Availability check"
$r = Post "$g/requests/$($rq.id)/review" @{} $hs
Check "APPROVED" ($r.status -eq "APPROVED")
Check "approved = 20" ([decimal]$r.items[0].approvedQuantity -eq 20)

# ---------------- 5. reserve ----------------
Step "5. Reserve"
$r = Post "$g/requests/$($rq.id)/reserve" @{} $hs
Check "RESERVED" ($r.status -eq "RESERVED")
Check "reserved = 20" ([decimal]$r.items[0].reservedQuantity -eq 20)
$res = @(GetApi "$g/reservations?requestId=$($rq.id)" $hs)
$activeSum = ($res | Where-Object { $_.status -eq "ACTIVE" } | Measure-Object -Property quantity -Sum).Sum
Check "inventory: active reservations total 20" ($activeSum -eq 20)
Check "available decreased by 20" ((Available) -eq ($av - 20))
Check "repeat reserve rejected (409)" ((StatusOf { Post "$g/requests/$($rq.id)/reserve" @{} $hs }) -eq 409)
$hist = @(GetApi "$g/requests/$($rq.id)/history" $hu)
Check "history has 4 transitions" ($hist.Count -eq 4)

# ---------------- 6. partial, decision, cancel ----------------
Step "6. Partial availability, decision, cancel"
$left = [int](Available)
$rq2 = Post "$g/requests" @{ warehouseId=$wh.id; items=@(@{ productId=$prod.id; quantity=($left + 15) }) } $hu2
Post "$g/requests/$($rq2.id)/submit" @{} $hu2 | Out-Null
$r2 = Post "$g/requests/$($rq2.id)/review" @{} $hs
Check "PARTIALLY_AVAILABLE" ($r2.status -eq "PARTIALLY_AVAILABLE")
Check "unavailable = 15" ([decimal]$r2.items[0].unavailableQuantity -eq 15)
$pick = $left - 1
$r2 = Post "$g/requests/$($rq2.id)/reserve" @{ items=@(@{ itemId=$r2.items[0].id; approvedQuantity=$pick }) } $hs
Check "RESERVED_PARTIAL" ($r2.status -eq "RESERVED_PARTIAL")
Check "reserved = $pick" ([decimal]$r2.items[0].reservedQuantity -eq $pick)
Check "available = 1 after partial reserve" ((Available) -eq 1)
Check "user cannot cancel a reserved request (409)" ((StatusOf { Post "$g/requests/$($rq2.id)/cancel" @{ reason="x" } $hu2 }) -eq 409)
$c = Post "$g/requests/$($rq2.id)/cancel" @{ reason="Test cancel" } $hs
Check "CANCELLED" ($c.status -eq "CANCELLED")
Check "reservation released, available restored" ((Available) -eq $left)
$closed = Post "$g/requests/$($rq2.id)/close" @{} $hs
Check "CLOSED with closedAt" ($closed.status -eq "CLOSED" -and $null -ne $closed.closedAt)

# ---------------- 7. integrity ----------------
Step "7. Integrity (database)"
$prev = $ErrorActionPreference
$ErrorActionPreference = "Continue"   # psql writes errors to stderr: do not stop the script on them
$bad = (docker exec warehouse-postgres psql -U warehouse -d inventory_db -t -A -c "select count(*) from (select s.id from stock s left join reservations r on r.stock_id = s.id group by s.id having s.reserved_quantity <> coalesce(sum(r.quantity) filter (where r.status='ACTIVE'),0)) t" | Out-String)
$del = (docker exec warehouse-postgres psql -U warehouse -d request_db -c "delete from request_status_history" 2>&1 | Out-String)
$ErrorActionPreference = $prev
Check "stock.reserved equals the sum of active reservations" ($bad.Trim() -eq "0")
Check "status history is immutable (DB rejects delete)" ($del -match "ERROR" -and $del -match "request_status_history")

Write-Host ""
if ($script:failed -eq 0) { Write-Host "ALL CHECKS PASSED" -ForegroundColor Green }
else { Write-Host "$($script:failed) CHECK(S) FAILED" -ForegroundColor Red }