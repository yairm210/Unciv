param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [int]$AppCodeNumber = 0
)

$ErrorActionPreference = "Stop"

if ($Version -notmatch '^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$') {
    throw "Version must use SemVer format, for example 0.1.0 or 0.1.0-beta.1"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$buildConfigPath = Join-Path $repoRoot "buildSrc\src\main\kotlin\BuildConfig.kt"
$uncivGamePath = Join-Path $repoRoot "core\src\com\unciv\UncivGame.kt"
$changelogPath = Join-Path $repoRoot "RECIV_CHANGELOG.md"

$buildConfig = Get-Content -Raw -LiteralPath $buildConfigPath
$currentCodeMatch = [regex]::Match($buildConfig, 'appCodeNumber = (\d+)')
if (-not $currentCodeMatch.Success) {
    throw "Could not find appCodeNumber in $buildConfigPath"
}

if ($AppCodeNumber -le 0) {
    $AppCodeNumber = [int]$currentCodeMatch.Groups[1].Value + 1
}

$buildConfig = [regex]::Replace($buildConfig, 'appVersion = ".*"', "appVersion = `"$Version`"")
$buildConfig = [regex]::Replace($buildConfig, 'appCodeNumber = \d+', "appCodeNumber = $AppCodeNumber")
Set-Content -LiteralPath $buildConfigPath -Value $buildConfig -NoNewline

$uncivGame = Get-Content -Raw -LiteralPath $uncivGamePath
$uncivGame = [regex]::Replace($uncivGame, 'Version\(".*", \d+\)', "Version(`"$Version`", $AppCodeNumber)")
Set-Content -LiteralPath $uncivGamePath -Value $uncivGame -NoNewline

if (-not (Test-Path -LiteralPath $changelogPath)) {
    Set-Content -LiteralPath $changelogPath -Value "# Reciv Changelog`r`n`r`n## Unreleased`r`n"
}

$changelog = Get-Content -Raw -LiteralPath $changelogPath
if ($changelog -notmatch "(?m)^## $([regex]::Escape($Version))(\s|$)") {
    $today = Get-Date -Format "yyyy-MM-dd"
    $section = "## $Version - $today`r`n`r`n- TODO: summarize this Reciv release.`r`n`r`n"
    if ($changelog -match "(?m)^## Unreleased\s*$") {
        $changelog = [regex]::Replace($changelog, "(?m)^## Unreleased\s*", "## Unreleased`r`n`r`n$section", 1)
    } else {
        $changelog = $changelog.TrimEnd() + "`r`n`r`n$section"
    }
    Set-Content -LiteralPath $changelogPath -Value $changelog -NoNewline
}

Write-Host "Prepared Reciv release $Version with appCodeNumber $AppCodeNumber"
