# scenario-13-refresh.ps1 (ASCII only): P1-8, short access token + refresh token with rotation and revocation.
# Run after restarting auth-service and api-gateway:  . .\scripts\dev-env.ps1 ; .\scripts\scenario-13-refresh.ps1
# The script reads the refresh cookie from Set-Cookie and sends it back as a Cookie header by hand,
# so it works over plain HTTP even though the cookie has the Secure flag.
. "$PSScriptRoot\helpers.ps1"

function Raw($method, $uri, $obj, $headers) {
  $p = @{ Method = $method; Uri = $uri; UseBasicParsing = $true }
  if ($headers) { $p.Headers = $headers }
  if ($null -ne $obj) {
    $p.ContentType = "application/json; charset=utf-8"
    $p.Body = [Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 8))
  }
  try {
    $r = Invoke-WebRequest @p
    $text = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    $parsed = $null
    if ($text) { $parsed = $text | ConvertFrom-Json }
    return @{ Status = [int]$r.StatusCode; Body = $parsed; Code = ""; SetCookie = "$($r.Headers['Set-Cookie'])" }
  } catch {
    $resp = $_.Exception.Response
    if (-not $resp) { throw }
    $code = ""
    if ($_.ErrorDetails.Message) { try { $code = ($_.ErrorDetails.Message | ConvertFrom-Json).code } catch { } }
    return @{ Status = [int]$resp.StatusCode; Body = $null; Code = $code; SetCookie = "" }
  }
}
function CookieValue($setCookie) { if ($setCookie -match 'wms_refresh=([^;]*)') { $Matches[1] } else { $null } }
function LoginRaw($l, $p) { Raw "Post" "$g/auth/login" @{ login = $l; password = $p } @{} }
function RefreshWith($cookie) {
  $h = @{}
  if ($cookie) { $h.Cookie = "wms_refresh=$cookie" }
  Raw "Post" "$g/auth/refresh" $null $h
}
function JwtPayload($jwt) {
  $p = $jwt.Split('.')[1].Replace('-', '+').Replace('_', '/')
  switch ($p.Length % 4) { 2 { $p += '==' } 3 { $p += '=' } }
  [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json
}
function NewTempUser($roles) {
  $name = "rt" + (Get-Random -Maximum 9999999)
  $u = Post "$g/users" @{ login = $name; password = "User-12345"; fullName = "Refresh temp"; roles = $roles } $ha
  @{ Login = $name; Id = $u.id }
}

Step "Login issues a short access token and a refresh cookie"
$l = LoginRaw "store1" "Store-12345"
Check "login returns 200" ($l.Status -eq 200)
Check "cookie is HttpOnly" ($l.SetCookie -match "HttpOnly")
Check "cookie is SameSite=Strict" ($l.SetCookie -match "SameSite=Strict")
Check "cookie path is /api/v1/auth" ($l.SetCookie -match "Path=/api/v1/auth")
Check "refresh token is not in the JSON body" (-not (($l.Body.PSObject.Properties.Name -join ",") -match "refresh"))
$pl = JwtPayload $l.Body.accessToken
$ttl = [int]($pl.exp - $pl.iat)
Check "access token lives at most 20 minutes ($ttl s)" ($ttl -le 1200)
$c1 = CookieValue $l.SetCookie
$null = Need $c1 "refresh cookie value"

Step "Refresh rotates the token"
$r1 = RefreshWith $c1
Check "refresh returns 200" ($r1.Status -eq 200)
$c2 = CookieValue $r1.SetCookie
Check "a new refresh cookie is issued" ($c2 -and $c2 -ne $c1)
$me = Raw "Get" "$g/me" $null @{ Authorization = "Bearer $($r1.Body.accessToken)" }
Check "the new access token works (/me = 200)" ($me.Status -eq 200)

Step "Two tabs refreshing at once (reuse within the grace window)"
$race = RefreshWith $c1
Check "second use of the old token inside the grace window returns 200" ($race.Status -eq 200)
Check "no new cookie is set in that case" ($race.SetCookie -eq "")
$r2 = RefreshWith $c2
Check "the newest token still works (session is not revoked)" ($r2.Status -eq 200)
$c3 = CookieValue $r2.SetCookie

Step "Reuse of an old token after the grace window revokes the whole session"
Write-Host "  waiting 12 s for the grace window to pass..."
Start-Sleep -Seconds 12
$stolen = RefreshWith $c1
Check "old token is rejected (401)" ($stolen.Status -eq 401)
Check "error code is INVALID_REFRESH_TOKEN" ($stolen.Code -eq "INVALID_REFRESH_TOKEN")
$after = RefreshWith $c3
Check "the newest token of that session is revoked too (401)" ($after.Status -eq 401)

Step "Logout revokes the session"
$l2 = LoginRaw "store1" "Store-12345"
$c = CookieValue $l2.SetCookie
$out = Raw "Post" "$g/auth/logout" $null @{ Cookie = "wms_refresh=$c" }
Check "logout returns 204" ($out.Status -eq 204)
Check "refresh after logout is rejected (401)" ((RefreshWith $c).Status -eq 401)
Check "logout without a cookie is harmless (204)" ((Raw "Post" "$g/auth/logout" $null @{}).Status -eq 204)

Step "Missing or garbage cookie"
$none = RefreshWith $null
Check "no cookie: 401 INVALID_REFRESH_TOKEN" ($none.Status -eq 401 -and $none.Code -eq "INVALID_REFRESH_TOKEN")
$junk = RefreshWith "not-a-real-token"
Check "garbage cookie: 401 INVALID_REFRESH_TOKEN" ($junk.Status -eq 401 -and $junk.Code -eq "INVALID_REFRESH_TOKEN")

Step "Disabling a user ends all his sessions"
$t1 = NewTempUser @("USER")
$lt = LoginRaw $t1.Login "User-12345"
$ct = CookieValue $lt.SetCookie
Patch "$g/users/$($t1.Id)" @{ active = $false } $ha | Out-Null
Check "refresh of a disabled user is rejected (401)" ((RefreshWith $ct).Status -eq 401)

Step "Changing roles ends all sessions of the user"
$t2 = NewTempUser @("USER")
$lt2 = LoginRaw $t2.Login "User-12345"
$ct2 = CookieValue $lt2.SetCookie
Patch "$g/users/$($t2.Id)" @{ roles = @("USER", "WAREHOUSE_WORKER") } $ha | Out-Null
Check "refresh after a role change is rejected (401)" ((RefreshWith $ct2).Status -eq 401)

Summary
