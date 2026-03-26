# Performance Benchmarking

This document describes the repeatable cache and runtime benchmarking workflow for
`spring-correctness-linter`.

## Purpose

Use this workflow when a change may affect:

- incremental-cache reuse
- cache invalidation scope
- runtime metrics or logging
- multi-module report generation
- source-root or semantic-fingerprint behavior

The goal is not to produce perfect microbenchmarks. The goal is to compare:

- cold vs warm runs
- reported runtime metrics vs wall-clock time
- cache hit behavior across representative sample projects

## Script

Use the repository script:

- `scripts/benchmark-cache.ps1`

The script:

- installs the current repository artifacts into the local Maven repository by default
- runs a cold benchmark with `clean verify -DskipTests`
- runs one or more warm benchmarks with `verify -DskipTests`
- reads each sample's `lint-report.json`
- prints a console table and writes artifacts under `target/benchmark-cache/`

Generated artifacts:

- `target/benchmark-cache/benchmark-cache-results.json`
- `target/benchmark-cache/benchmark-cache-results.md`

## Supported Targets

- `reactor`
- `adoption-basic`
- `adoption-centralized-security`
- `adoption-cache-convention`
- `adoption-all`
- `all`

Default target set:

- `reactor`
- `adoption-basic`
- `adoption-centralized-security`
- `adoption-cache-convention`

## Usage

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/benchmark-cache.ps1
```

PowerShell 7:

```powershell
pwsh -File scripts/benchmark-cache.ps1
```

Only benchmark the reactor sample with two warm runs:

```powershell
pwsh -File scripts/benchmark-cache.ps1 -Targets reactor -WarmRuns 2
```

Only benchmark adoption-style apps without reinstalling local artifacts:

```powershell
pwsh -File scripts/benchmark-cache.ps1 -Targets adoption-all -WarmRuns 1 -SkipInstall
```

## Output Interpretation

Each run records:

- `WallClockMs`: shell-level elapsed time for the Maven command
- `ReportedTotalMs`: linter runtime from `lint-report.json`
- `CachedFileCount`
- `CacheHitRatePercent`
- `CacheMissReasons`
- `IssueCount`

Recommended reading:

1. Compare cold vs warm `CachedFileCount`
2. Check whether warm runs still report cache miss reasons
3. Compare `WallClockMs` to `ReportedTotalMs`
4. For reactor runs, confirm that warm runs still keep expected issue counts

## Recommended Maintainer Workflow

When a change affects cache behavior or runtime metrics:

1. Run `pwsh -File scripts/benchmark-cache.ps1 -Targets reactor,adoption-all -WarmRuns 1`
2. Save the generated markdown artifact if the change will be discussed in a PR
3. If warm runs stop improving, inspect:
   - `cacheMissReasons`
   - source-root changes
   - semantic fingerprint scope
   - sample-specific report paths

## Notes

- Maven startup and project build work still dominate small samples, so wall-clock differences may remain modest even when cache reuse is correct.
- Prefer comparing trends across the same machine and the same JDK rather than comparing absolute times between environments.
- Use the sample projects in `samples/reactor-sample/` and `samples/adoption-suite/` as the default benchmark surface unless a change is specific to another sample.
