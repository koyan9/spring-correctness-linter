# spring-correctness-linter v0.1.3

## Highlights

- Hardened incremental-cache correctness so project-level semantic changes invalidate cache reuse safely instead of silently restoring stale findings
- Added cache miss observability across JSON reports, HTML reports, and Maven logs, plus a repeatable cold/warm benchmark workflow for sample projects
- Tightened low-noise rule accuracy for inherited endpoint security and Spring cache container declarations

## Included Rule Areas

- Added:
  - None
- Expanded:
  - `SPRING_ENDPOINT_SECURITY` now avoids inheriting method-level security intent across overloads that only match by method name and parameter count
  - `SPRING_CACHEABLE_KEY` now recognizes Spring's `@Caching(cacheable = @Cacheable(...))` container form for explicit key-strategy checks and default-key allowlists
- Removed or deprecated:
  - None

## Tooling

- Maven plugin changes:
  - Cache miss reasons are now emitted before generic cache hints when incremental cache reuse is skipped
  - PR and release-note templates now ask for benchmark evidence when cache, runtime, or performance-sensitive behavior changes
- Report generation changes:
  - `lint-report.json` now includes `runtimeMetrics.cacheMissReasons`
  - HTML runtime metrics now show cache miss reasons alongside cache scope, hit rate, and phase timing
- Sample project changes:
  - Added a repeatable benchmark script for `samples/reactor-sample/` and `samples/adoption-suite/`
  - Maintainer and release docs now describe how to capture cold vs warm sample runs

## Breaking Changes

- None

## Reports and CI

- JSON / HTML report changes:
  - Runtime metrics now expose cache miss reasons in both machine-readable and human-readable outputs
- SARIF or code scanning changes:
  - No SARIF schema change in this release
- Baseline / baseline diff changes:
  - Stale baseline entries from additional or non-standard source roots remain mapped to the correct module more reliably
- Multi-module, reactor, or cache behavior changes:
  - Cache fingerprints now include semantic options and source-derived semantic context
  - Fingerprint scope is now selective, so type-resolution and auto-detect summaries are only built when the active rule set or options actually need them
  - Cache-file loading now stops early on metadata mismatch instead of parsing the full file first
- Performance or cache evidence:
  - `scripts/benchmark-cache.ps1 -Targets reactor -WarmRuns 1` produced:
    - cold: `WallClockMs=4920`, `ReportedTotalMs=259`, `CachedFileCount=0`, `CacheMissReasons=cache-files-missing`
    - warm: `WallClockMs=3581`, `ReportedTotalMs=216`, `CachedFileCount=4`, `CacheHitRatePercent=100`

## Upgrade and Adoption Notes

- Recommended Maven configuration changes:
  - No new Maven properties were introduced in this release
  - Teams diagnosing cache reuse should start with `runtimeMetrics.cacheMissReasons` and the benchmark script before changing cache configuration
- New or renamed Maven properties:
  - None
- Cache invalidation or analysis fingerprint notes:
  - Cache reuse now invalidates on semantic option changes, source-root composition changes, annotation/type-context changes, and auto-detect-context changes
  - Cache invalidation remains conservative by design; the new benchmark script is the recommended way to measure cold vs warm behavior after upgrades
- PowerShell, sample, or CI workflow notes:
  - PowerShell benchmark entrypoint: `powershell -ExecutionPolicy Bypass -File scripts\\benchmark-cache.ps1`
  - Contributor and release workflows now include benchmark evidence when performance-sensitive behavior changes

## Verification

- `./mvnw.cmd -q verify`
- `./mvnw.cmd -q -Prelease-artifacts verify`
- `./mvnw.cmd -q -Pcentral-publish -DskipTests verify`
- `cmd /c mvn -q -f samples\\vulnerable-sample\\pom.xml -DskipTests verify`
- `cmd /c mvn -q -f samples\\reactor-sample\\pom.xml clean verify -DskipTests`
- `cmd /c mvn -q -f samples\\adoption-suite\\pom.xml -DskipTests verify`
- `powershell -ExecutionPolicy Bypass -File scripts\\benchmark-cache.ps1 -Targets reactor -WarmRuns 1`
