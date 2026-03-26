param(
    [string[]]$Targets = @("reactor", "adoption-basic", "adoption-centralized-security", "adoption-cache-convention"),
    [int]$WarmRuns = 1,
    [switch]$SkipInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($WarmRuns -lt 1) {
    throw "WarmRuns must be >= 1."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$targetCatalog = @{
    "reactor" = @{
        Label = "reactor-sample"
        Pom = "samples/reactor-sample/pom.xml"
        Report = "samples/reactor-sample/target/spring-correctness-linter/lint-report.json"
    }
    "adoption-basic" = @{
        Label = "adoption-suite/basic-app"
        Pom = "samples/adoption-suite/basic-app/pom.xml"
        Report = "samples/adoption-suite/basic-app/target/spring-correctness-linter/lint-report.json"
    }
    "adoption-centralized-security" = @{
        Label = "adoption-suite/centralized-security-app"
        Pom = "samples/adoption-suite/centralized-security-app/pom.xml"
        Report = "samples/adoption-suite/centralized-security-app/target/spring-correctness-linter/lint-report.json"
    }
    "adoption-cache-convention" = @{
        Label = "adoption-suite/cache-convention-app"
        Pom = "samples/adoption-suite/cache-convention-app/pom.xml"
        Report = "samples/adoption-suite/cache-convention-app/target/spring-correctness-linter/lint-report.json"
    }
}

function Resolve-BenchmarkTargets {
    param([string[]]$RequestedTargets)

    $expanded = New-Object System.Collections.Generic.List[string]
    foreach ($target in $RequestedTargets) {
        switch ($target) {
            "all" {
                $expanded.AddRange(@(
                    "reactor",
                    "adoption-basic",
                    "adoption-centralized-security",
                    "adoption-cache-convention"
                ))
            }
            "adoption-all" {
                $expanded.AddRange(@(
                    "adoption-basic",
                    "adoption-centralized-security",
                    "adoption-cache-convention"
                ))
            }
            default {
                if (-not $targetCatalog.ContainsKey($target)) {
                    throw "Unknown benchmark target '$target'. Available targets: $($targetCatalog.Keys -join ', '), all, adoption-all."
                }
                $expanded.Add($target)
            }
        }
    }
    return $expanded | Select-Object -Unique
}

function Invoke-Maven {
    param([string[]]$Arguments)

    if ($env:OS -eq "Windows_NT") {
        & ".\mvnw.cmd" @Arguments
    } else {
        & "./mvnw" @Arguments
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Maven command failed: $($Arguments -join ' ')"
    }
}

function Read-BenchmarkReport {
    param([string]$ReportPath)

    if (-not (Test-Path $ReportPath)) {
        throw "Expected report not found: $ReportPath"
    }
    return Get-Content $ReportPath -Raw | ConvertFrom-Json
}

function Invoke-BenchmarkRun {
    param(
        [string]$TargetKey,
        [string]$Mode,
        [string[]]$MavenArguments
    )

    $entry = $targetCatalog[$TargetKey]
    Write-Host "==> $($entry.Label) [$Mode]"
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-Maven $MavenArguments
    $stopwatch.Stop()
    $report = Read-BenchmarkReport $entry.Report

    return [pscustomobject]@{
        TargetKey = $TargetKey
        Target = $entry.Label
        Mode = $Mode
        WallClockMs = [int][math]::Round($stopwatch.Elapsed.TotalMilliseconds, 0)
        ReportedTotalMs = [int]$report.runtimeMetrics.totalElapsedMillis
        SourceFileCount = [int]$report.runtimeMetrics.sourceFileCount
        AnalyzedFileCount = [int]$report.runtimeMetrics.analyzedFileCount
        CachedFileCount = [int]$report.runtimeMetrics.cachedFileCount
        CacheHitRatePercent = [int]$report.runtimeMetrics.cacheHitRatePercent
        CacheMissReasons = $(if ($null -ne $report.runtimeMetrics.cacheMissReasons) { $report.runtimeMetrics.cacheMissReasons -join "," } else { "" })
        IssueCount = [int]$report.summary.issueCount
        ReportPath = $entry.Report
    }
}

function Write-BenchmarkArtifacts {
    param([object[]]$Results)

    $outputDirectory = Join-Path $repoRoot "target/benchmark-cache"
    New-Item -ItemType Directory -Force $outputDirectory | Out-Null

    $jsonPath = Join-Path $outputDirectory "benchmark-cache-results.json"
    $markdownPath = Join-Path $outputDirectory "benchmark-cache-results.md"

    $Results | ConvertTo-Json -Depth 5 | Set-Content -Path $jsonPath

    $markdown = New-Object System.Text.StringBuilder
    [void]$markdown.AppendLine("# Cache Benchmark Results")
    [void]$markdown.AppendLine("")
    [void]$markdown.AppendLine("| Target | Mode | WallClockMs | ReportedTotalMs | CachedFileCount | CacheHitRatePercent | CacheMissReasons | IssueCount |")
    [void]$markdown.AppendLine("| --- | --- | ---: | ---: | ---: | ---: | --- | ---: |")
    foreach ($result in $Results) {
        [void]$markdown.AppendLine("| $($result.Target) | $($result.Mode) | $($result.WallClockMs) | $($result.ReportedTotalMs) | $($result.CachedFileCount) | $($result.CacheHitRatePercent) | $($result.CacheMissReasons) | $($result.IssueCount) |")
    }
    Set-Content -Path $markdownPath -Value $markdown.ToString()

    Write-Host ""
    Write-Host "Wrote benchmark artifacts:"
    Write-Host "  $jsonPath"
    Write-Host "  $markdownPath"
}

$resolvedTargets = Resolve-BenchmarkTargets $Targets

if (-not $SkipInstall) {
    Write-Host "==> Installing current artifacts into the local Maven repository"
    Invoke-Maven @("-q", "-DskipTests", "install")
}

$results = New-Object System.Collections.Generic.List[object]
foreach ($target in $resolvedTargets) {
    $entry = $targetCatalog[$target]
    $coldArgs = @("-q", "-f", $entry.Pom, "clean", "verify", "-DskipTests")
    $results.Add((Invoke-BenchmarkRun -TargetKey $target -Mode "cold" -MavenArguments $coldArgs))
    for ($index = 1; $index -le $WarmRuns; $index++) {
        $warmArgs = @("-q", "-f", $entry.Pom, "verify", "-DskipTests")
        $results.Add((Invoke-BenchmarkRun -TargetKey $target -Mode "warm-$index" -MavenArguments $warmArgs))
    }
}

Write-Host ""
$results | Format-Table Target, Mode, WallClockMs, ReportedTotalMs, CachedFileCount, CacheHitRatePercent, CacheMissReasons, IssueCount -AutoSize

Write-BenchmarkArtifacts $results
