. "$PSScriptRoot\helpers.ps1"

Step "Setup"
Ensure-Stock 90
$av = Available
Write-Host "  available: $av, worker1: $w1, worker2: $w2"

# ---------------- 1. request up to RESERVED, admin read access ----------------
Step "1. Request for 60 pcs: reserve, admin read access"
$r = New-ReservedRequest 60
Need $r.id "request id" | Out-Null
Check "RESERVED" ($r.status -eq "RESERVED")
Check "admin can read the request" ((GetApi "$g/requests/$($r.id)" $ha).number -eq $r.number)
Check "admin cannot review (403)" ((StatusOf { Post "$g/requests/$($r.id)/review" @{} $ha }) -eq 403)

# ---------------- 2. picking task ----------------
Step "2. Picking task with two workers"
Check "non-worker rejected (400)" ((StatusOf { Post "$g/picking-tasks" @{ requestId=$r.id; workers=@(@{ workerId=$u1 }) } $hs }) -eq 400)
Check "worker cannot create task (403)" ((StatusOf { Post "$g/picking-tasks" @{ requestId=$r.id } $hw }) -eq 403)
$t = Post "$g/picking-tasks" @{ requestId=$r.id; workers=@(@{ workerId=$w1; role="PRIMARY" }, @{ workerId=$w2 }) } $hs
Need $t.id "task id" | Out-Null
Check "task NEW with 2 workers" ($t.status -eq "NEW" -and @($t.workers).Count -eq 2)
Check "request moved to PICKING" ((GetApi "$g/requests/$($r.id)" $hs).status -eq "PICKING")
Check "task rows total 60" ((@($t.items) | Measure-Object -Property requestedQuantity -Sum).Sum -eq 60)
Check "nothing assigned yet" ([decimal]$t.progress.unassignedQuantity -eq 60)
Check "second open task rejected (409)" ((StatusOf { Post "$g/picking-tasks" @{ requestId=$r.id } $hs }) -eq 409)

# ---------------- 3. assignment between workers ----------------
Step "3. Split 60 pcs: worker1 gets 40, worker2 gets 20"
$ri = @($t.items)[0].requestItemId
Check "over-assignment rejected (409)" ((StatusOf { Post "$g/picking-tasks/$($t.id)/assign-items" @{ assignments=@(@{ requestItemId=$ri; workerId=$w1; quantity=61 }) } $hs }) -eq 409)
$a = Post "$g/picking-tasks/$($t.id)/assign-items" @{ assignments=@(@{ requestItemId=$ri; workerId=$w1; quantity=40 }, @{ requestItemId=$ri; workerId=$w2; quantity=20 }) } $hs
$rows1 = @($a.items | Where-Object { $_.assignedWorkerId -eq $w1 })
$rows2 = @($a.items | Where-Object { $_.assignedWorkerId -eq $w2 })
Check "worker1 has 40" (($rows1 | Measure-Object -Property requestedQuantity -Sum).Sum -eq 40)
Check "worker2 has 20" (($rows2 | Measure-Object -Property requestedQuantity -Sum).Sum -eq 20)
Check "nothing left unassigned" ([decimal]$a.progress.unassignedQuantity -eq 0)

# ---------------- 4. what a worker sees ----------------
Step "4. Worker view"
$v2 = GetApi "$g/picking-tasks/$($t.id)" $hw2
Check "worker2 sees only own rows" (@($v2.items).Count -eq $rows2.Count -and @($v2.items | Where-Object { $_.assignedWorkerId -ne $w2 }).Count -eq 0)
Check "worker2 sees overall progress" ([decimal]$v2.progress.totalQuantity -eq 60)
Check "worker2 progress lists only himself" (@($v2.progress.workers).Count -eq 1)
Check "worker2 cannot pick worker1 row (403)" ((StatusOf { Post "$g/picking-tasks/$($t.id)/items/$($rows1[0].id)/pick" @{ pickedQuantity=1 } $hw2 }) -eq 403)
Check "storekeeper cannot pick (403)" ((StatusOf { Post "$g/picking-tasks/$($t.id)/items/$($rows1[0].id)/pick" @{ pickedQuantity=1 } $hs }) -eq 403)
Check "non-participant sees no task (404)" ((StatusOf { GetApi "$g/picking-tasks/$($t.id)" $hu }) -eq 403)

# ---------------- 5. picking with a shortage ----------------
Step "5. Picking: worker1 exact, worker2 short by 1"
foreach ($row in $rows1) {
  $x = Post "$g/picking-tasks/$($t.id)/items/$($row.id)/pick" @{ pickedQuantity=$row.requestedQuantity } $hw
}
$first2 = $rows2[0]
$short = [decimal]$first2.requestedQuantity - 1
Check "over-pick rejected (409)" ((StatusOf { Post "$g/picking-tasks/$($t.id)/items/$($first2.id)/pick" @{ pickedQuantity=([decimal]$first2.requestedQuantity + 1) } $hw2 }) -eq 409)
Check "shortage without reason rejected (400)" ((StatusOf { Post "$g/picking-tasks/$($t.id)/items/$($first2.id)/pick" @{ pickedQuantity=$short } $hw2 }) -eq 400)
Check "task still open" ((GetApi "$g/picking-tasks/$($t.id)" $hs).status -eq "IN_PROGRESS")
Post "$g/picking-tasks/$($t.id)/items/$($first2.id)/pick" @{ pickedQuantity=$short; comment="one unit damaged" } $hw2 | Out-Null
foreach ($row in @($rows2 | Select-Object -Skip 1)) {
  Post "$g/picking-tasks/$($t.id)/items/$($row.id)/pick" @{ pickedQuantity=$row.requestedQuantity } $hw2 | Out-Null
}
$done = GetApi "$g/picking-tasks/$($t.id)" $hs
Check "task COMPLETED" ($done.status -eq "COMPLETED")
Check "progress 100 percent" ([decimal]$done.progress.percent -eq 100)
Check "picked total 59" ([decimal]$done.progress.pickedQuantity -eq 59)
Check "both workers COMPLETED" (@($done.workers | Where-Object { $_.status -eq "COMPLETED" }).Count -eq 2)
Check "request PICKING_WITH_SHORTAGE" ((GetApi "$g/requests/$($r.id)" $hs).status -eq "PICKING_WITH_SHORTAGE")

# ---------------- 6. check results and issue ----------------
Step "6. Ready for issue, issue"
Check "worker cannot mark ready (403)" ((StatusOf { Post "$g/requests/$($r.id)/ready-for-issue" @{} $hw }) -eq 403)
Check "issue before ready rejected (409)" ((StatusOf { Post "$g/requests/$($r.id)/issue" @{} $hs }) -eq 409)
Check "READY_FOR_ISSUE" ((Post "$g/requests/$($r.id)/ready-for-issue" @{ comment="Checked" } $hs).status -eq "READY_FOR_ISSUE")
$issued = Post "$g/requests/$($r.id)/issue" @{ comment="Handover" } $hs
Check "request CLOSED after issue" ($issued.status -eq "CLOSED")
Check "issue document number set" ($null -ne $issued.issueNumber -and "$($issued.issueNumber)" -ne "")
Check "issued = 59" ([decimal]$issued.items[0].issuedQuantity -eq 59)
Check "available = before - 59 (shortage unit returned)" ((Available) -eq ($av - 59))
$res = @(GetApi "$g/reservations?requestId=$($r.id)" $hs)
Check "no ACTIVE reservations left" (@($res | Where-Object { $_.status -eq "ACTIVE" }).Count -eq 0)
Check "repeat issue rejected (409)" ((StatusOf { Post "$g/requests/$($r.id)/issue" @{} $hs }) -eq 409)
$hist = @(GetApi "$g/requests/$($r.id)/history" $hu)
Check "history ends with ISSUED then CLOSED" ($hist[-2].toStatus -eq "ISSUED" -and $hist[-1].toStatus -eq "CLOSED")

# ---------------- 7. replacing a worker, then cancel in picking ----------------
Step "7. Replace worker, cancel during picking"
$r2 = New-ReservedRequest 10
$t2 = Post "$g/picking-tasks" @{ requestId=$r2.id; workers=@(@{ workerId=$w1 }, @{ workerId=$w2 }) } $hs
$ri2 = @($t2.items)[0].requestItemId
Post "$g/picking-tasks/$($t2.id)/assign-items" @{ assignments=@(@{ requestItemId=$ri2; workerId=$w1; quantity=10 }) } $hs | Out-Null
Check "remove worker with unfinished rows needs replacement (409)" ((StatusOf { Api "Delete" "$g/picking-tasks/$($t2.id)/workers/$w1`?reason=shift-change" $null $hs }) -eq 409)
$moved = Api "Delete" "$g/picking-tasks/$($t2.id)/workers/$w1`?replaceWith=$w2&reason=shift-change" $null $hs
Check "rows moved to worker2" (@($moved.items | Where-Object { $_.assignedWorkerId -eq $w2 }).Count -eq @($moved.items).Count)
Check "worker1 REASSIGNED" (@($moved.workers | Where-Object { $_.workerId -eq $w1 })[0].status -eq "REASSIGNED")
Check "worker2 is now PRIMARY" (@($moved.workers | Where-Object { $_.workerId -eq $w2 })[0].role -eq "PRIMARY")
$c = Post "$g/requests/$($r2.id)/cancel" @{ reason="Test cancel in picking" } $hs
Check "request CANCELLED" ($c.status -eq "CANCELLED")
Check "task REJECTED" ((GetApi "$g/picking-tasks/$($t2.id)" $hs).status -eq "REJECTED")
Check "reservation released" (@(GetApi "$g/reservations?requestId=$($r2.id)" $hs | Where-Object { $_.status -eq "ACTIVE" }).Count -eq 0)

# ---------------- 8. integrity ----------------
Step "8. Integrity (database)"
$prev = $ErrorActionPreference; $ErrorActionPreference = "Continue"
$bad = (docker exec warehouse-postgres psql -U warehouse -d inventory_db -t -A -c "select count(*) from (select s.id from stock s left join reservations r on r.stock_id = s.id group by s.id having s.reserved_quantity <> coalesce(sum(r.quantity) filter (where r.status='ACTIVE'),0)) t" | Out-String)
$reass = (docker exec warehouse-postgres psql -U warehouse -d request_db -t -A -c "select count(*) from picking_reassignments" | Out-String)
$ErrorActionPreference = $prev
Check "stock.reserved equals the sum of active reservations" ($bad.Trim() -eq "0")
Check "reassignment is logged" ([int]$reass.Trim() -ge 1)

Summary