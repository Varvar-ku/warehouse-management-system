$g = "http://localhost:8080/api/v1"

function Api($method, $uri, $obj, $headers) {
  $p = @{ Method = $method; Uri = $uri; Headers = $headers; UseBasicParsing = $true }
  if ($null -ne $obj) {
    $p.ContentType = "application/json; charset=utf-8"
    $p.Body = [Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 8))
  }
  $r = Invoke-WebRequest @p
  $text = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
  if ($text) { $parsed = $text | ConvertFrom-Json; $parsed }   # через переменную: массивы разворачиваются в конвейер
}
function Post($uri, $obj, $headers)  { Api "Post"  $uri $obj $headers }
function Patch($uri, $obj, $headers) { Api "Patch" $uri $obj $headers }
function GetApi($uri, $headers)      { Api "Get"   $uri $null $headers }
function TryPost($uri, $obj, $headers) {
  try { Post $uri $obj $headers }
  catch { "HTTP " + [int]$_.Exception.Response.StatusCode; $_.ErrorDetails.Message }
}
function TryGet($uri, $headers) {
  try { GetApi $uri $headers }
  catch { "HTTP " + [int]$_.Exception.Response.StatusCode; $_.ErrorDetails.Message }
}
function Login($l, $p) {
  $t = Post "$g/auth/login" @{ login=$l; password=$p } @{}
  @{ Authorization = "Bearer $($t.accessToken)" }
}
function WithKey($h) { $h + @{ "Idempotency-Key" = [guid]::NewGuid().Guid } }   # заголовки + новый Idempotency-Key