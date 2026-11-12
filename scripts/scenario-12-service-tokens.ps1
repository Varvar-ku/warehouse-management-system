# scenario-12-service-tokens.ps1 (ASCII only): P1-10, internal operations of inventory-service are for request-service only.
# Run after restarting all services:  . .\scripts\dev-env.ps1 ; .\scripts\scenario-12-service-tokens.ps1
# P2-11: tokens are RS256, nobody outside auth-service can sign a valid one. The "forged tokens" part now checks
# that tokens signed with the old shared HS256 secret (and unsigned tokens) are REJECTED everywhere.
. "$PSScriptRoot\helpers.ps1"

$inv = "http://localhost:8084/api/v1"
$auth = "http://localhost:8081/api/v1"
# old shared secrets: the public dev one and, if still set, the JWT_SECRET of a previous setup
$oldSecrets = @("dev-only-secret-change-me-0123456789abcdef")
if ($env:JWT_SECRET) { $oldSecrets += $env:JWT_SECRET }

function B64Url([byte[]]$b) { [Convert]::ToBase64String($b).TrimEnd('=').Replace('+', '-').Replace('/', '_') }
function New-UnsignedJwt($claims) {
  $h = B64Url ([Text.Encoding]::UTF8.GetBytes((@{ alg = "none"; typ = "JWT" } | ConvertTo-Json -Compress)))
  $p = B64Url ([Text.Encoding]::UTF8.GetBytes(($claims | ConvertTo-Json -Compress)))
  "$h.$p."
}
function New-Jwt($claims, $key) {
  $h = B64Url ([Text.Encoding]::UTF8.GetBytes((@{ alg = "HS256"; typ = "JWT" } | ConvertTo-Json -Compress)))
  $p = B64Url ([Text.Encoding]::UTF8.GetBytes(($claims | ConvertTo-Json -Compress)))
  $hmac = New-Object System.Security.Cryptography.HMACSHA256 -ArgumentList (, [Text.Encoding]::UTF8.GetBytes($key))
  $sig = B64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$h.$p")))
  "$h.$p.$sig"
}
function Bearer($tok) { @{ Authorization = "Bearer $tok" } }
function Claims($roles, $svc, $sub) {
  $now = [int][DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
  $c = @{ iss = "warehouse-auth"; aud = "warehouse-api"; sub = $sub; iat = $now; exp = ($now + 120); jti = [guid]::NewGuid().Guid; roles = @($roles) }
  if ($svc) { $c.svc = $svc }
  $c
}
# 403 expected; 400 means the body was rejected before the access check (then the check is inconclusive)
function CheckDenied($name, $status) {
  if ($status -eq 403) { Check $name $true }
  elseif ($status -eq 400) { Write-Host "  SKIP  $name (body rejected before the access check; covered by RequestServiceOnlyTest)" -ForegroundColor Yellow }
  else { Check "$name (got HTTP $status)" $false }
}
function PassedAuth($status) { $status -ne 401 -and $status -ne 403 }

$rid = [guid]::NewGuid().Guid
$sid = (GetApi "$g/me" $hs).id
$release = @{ requestId = $rid }
$reserve = @{ requestId = $rid; warehouseId = $wh.id; items = @(@{ productId = $prod.id; quantity = 1 }) }
$issue = @{ requestId = $rid; warehouseId = $wh.id; issuedTo = $sid; items = @(@{ productId = $prod.id; quantity = 1 }) }

Step "Storekeeper cannot call internal operations (directly and through the gateway)"
CheckDenied "store1: release via inventory" (StatusOf { Api "Post" "$inv/reservations/release" $release $hs })
CheckDenied "store1: release via gateway" (StatusOf { Api "Post" "$g/reservations/release" $release $hs })
CheckDenied "store1: reserve" (StatusOf { Api "Post" "$g/reservations" $reserve (WithKey $hs) })
CheckDenied "store1: create issue" (StatusOf { Api "Post" "$g/issues" $issue $hs })
CheckDenied "store1: confirm issue" (StatusOf { Api "Post" "$g/issues/$rid/confirm" $null (WithKey $hs) })
CheckDenied "store1: cancel issue" (StatusOf { Api "Post" "$g/issues/$rid/cancel" @{ reason = "test" } $hs })

Step "Administrator cannot call them either"
CheckDenied "admin: release" (StatusOf { Api "Post" "$g/reservations/release" $release $ha })
CheckDenied "admin: confirm issue" (StatusOf { Api "Post" "$g/issues/$rid/confirm" $null (WithKey $ha) })

Step "Reading stays available to the storekeeper"
Check "store1: GET /reservations?requestId= works" ((StatusOf { Api "Get" "$g/reservations?requestId=$rid" $null $hs }) -eq 0)

Step "Forged tokens are rejected (RS256: no shared secret to sign with)"
$forged = @()
foreach ($s in $oldSecrets) {
  $forged += @{ Name = "HS256 STOREKEEPER token (old secret)"; Token = (New-Jwt (Claims @("STOREKEEPER") $null $sid) $s) }
  $forged += @{ Name = "HS256 ADMIN token (old secret)"; Token = (New-Jwt (Claims @("ADMIN") $null $sid) $s) }
  $forged += @{ Name = "HS256 service token (old secret)"; Token = (New-Jwt (Claims @("SERVICE") "request-service" $sid) $s) }
}
$forged += @{ Name = "unsigned (alg none) ADMIN token"; Token = (New-UnsignedJwt (Claims @("ADMIN") $null $sid)) }
$forged += @{ Name = "unsigned (alg none) service token"; Token = (New-UnsignedJwt (Claims @("SERVICE") "request-service" $sid)) }
foreach ($f in $forged) {
  $b = Bearer $f.Token
  Check ($f.Name + ": inventory 401") ((StatusOf { Api "Get" "$inv/reservations?requestId=$rid" $null $b }) -eq 401)
  Check ($f.Name + ": gateway 401") ((StatusOf { Api "Get" "$g/me" $null $b }) -eq 401)
  Check ($f.Name + ": auth 401") ((StatusOf { Api "Get" "$auth/me" $null $b }) -eq 401)
}
Write-Host "  (valid service tokens from request-service are exercised by scenario-5b: reserve, release and issue go through inventory)"

Summary
