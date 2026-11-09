<#
  git-cleanup.ps1  (ASCII only, PowerShell 5.1)

  Splits the accumulated working tree into thematic commits.
  Run from the repository root:

      .\scripts\git-cleanup.ps1 -DryRun     # only print what would happen
      .\scripts\git-cleanup.ps1             # make the commits
      .\scripts\git-cleanup.ps1 -Push       # make the commits and push origin main

  Prerequisite: the new .gitattributes and .gitignore are already in place
  (copied from the archive). Nothing is deleted from disk; zip files are only
  removed from the git index. Stray root files (package-lock.json) are not added.
#>
param(
    [switch]$DryRun,
    [switch]$Push
)

$ErrorActionPreference = 'Stop'

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GitArgs)
    Write-Host ("git " + ($GitArgs -join ' ')) -ForegroundColor DarkGray
    if ($DryRun) { return }
    & git @GitArgs
    if ($LASTEXITCODE -ne 0) { throw ("git failed: " + ($GitArgs -join ' ')) }
}

function Test-Staged {
    # returns $true when the index has staged changes
    & git diff --cached --quiet
    return ($LASTEXITCODE -ne 0)
}

function Commit-Group {
    param([string]$Message, [string[]]$Paths)
    $existing = @($Paths | Where-Object { Test-Path $_ })
    if ($existing.Count -eq 0) {
        Write-Host ("skip (paths not found): " + $Message) -ForegroundColor Yellow
        return
    }
    Invoke-Git add -A @existing
    if ($DryRun) { Write-Host ("would commit: " + $Message) -ForegroundColor Cyan; return }
    if (Test-Staged) {
        Invoke-Git commit -q -m $Message
        Write-Host ("committed: " + $Message) -ForegroundColor Green
    } else {
        Write-Host ("nothing to commit: " + $Message) -ForegroundColor Yellow
    }
}

# ---- preconditions ----
$top = (& git rev-parse --show-toplevel)
if ($LASTEXITCODE -ne 0) { throw "Not a git repository." }
$here = (Get-Location).Path.Replace('\', '/')
if ($top.TrimEnd('/') -ne $here.TrimEnd('/')) {
    throw ("Run the script from the repository root: " + $top)
}
if (-not (Test-Path '.gitattributes')) { throw ".gitattributes not found." }
if (Test-Staged) { throw "The index already has staged changes. Commit or reset them first." }

# autocrlf is irrelevant once .gitattributes sets eol=lf; switch it off to avoid mixed warnings
Invoke-Git config core.autocrlf false

# ---- 1. attributes, ignore rules, tracked archives ----
foreach ($z in @('report-service/report-service-names.zip', 'report-stock.zip')) {
    $tracked = (& git ls-files -- $z)
    if ($tracked) { Invoke-Git rm --cached -q $z }
}
Commit-Group 'chore: line endings (LF), ignore rules, untrack archives' @('.gitattributes', '.gitignore')
# the zips were staged by rm --cached above; they are committed together with the group

# ---- 2. thematic commits ----
Commit-Group 'build: root pom, maven wrapper'                      @('pom.xml', 'mvnw', 'mvnw.cmd', '.mvn')
Commit-Group 'feat(common-lib): events, outbox, numbering, service tokens' @('common-lib')
Commit-Group 'feat(auth): login throttle, audit events, role combinations'  @('auth-service')
Commit-Group 'feat(catalog): unit dimensions, usage guard, audit events'    @('catalog-service')
Commit-Group 'feat(warehouse): constraints validation, usage guard, audit'  @('warehouse-service')
Commit-Group 'feat(inventory): shortages, adjustments, section rules, expiry, tests' @('inventory-service')
Commit-Group 'feat(request): idempotency, operations lease, numbering, tests' @('request-service')
Commit-Group 'feat(gateway): routes and config'                    @('api-gateway', 'gateway')
Commit-Group 'feat(audit-service): immutable audit journal'        @('audit-service')
Commit-Group 'feat(notification-service): user notifications'      @('notification-service')
Commit-Group 'feat(report-service): read model and reports'        @('report-service')
Commit-Group 'feat(frontend): React client (stages 1-7)'           @('frontend')
Commit-Group 'chore: infrastructure and scripts'                   @('infrastructure', 'scripts')
Commit-Group 'docs: project notes'                                 @('README.md', 'README_ALL', 'IDEMPOTENCY-README.md', 'endpoints.txt', 'error-codes.txt')

# ---- 3. whatever is left among tracked files (mostly line endings) ----
Invoke-Git add --renormalize .
if (-not $DryRun) {
    if (Test-Staged) {
        Invoke-Git commit -q -m 'chore: normalize line endings and remaining changes'
        Write-Host 'committed: remaining changes' -ForegroundColor Green
    }
}

# ---- report ----
Write-Host ''
Write-Host '--- git status ---'
& git status --short
Write-Host '--- last commits ---'
& git log --oneline -20

if ($Push -and -not $DryRun) {
    Invoke-Git push origin main
} elseif (-not $DryRun) {
    Write-Host 'Not pushed. Review the log, then run: git push origin main'
}
