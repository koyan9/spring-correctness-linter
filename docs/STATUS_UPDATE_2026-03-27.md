# Stage Update - 2026-03-27

## Overview

This update summarizes the post-`v0.1.2` correctness and observability work completed on
`spring-correctness-linter`.

The focus of this batch was not on adding new rule families. Instead, it concentrated on:

- hardening incremental-cache invalidation boundaries around project-level semantic context
- making cache misses easier to explain in JSON reports, HTML reports, and Maven logs
- trimming avoidable semantic-fingerprint work when the active rule set does not need it
- adding a repeatable benchmarking workflow for reactor and adoption-style sample projects

## What Changed

### Incremental Cache Correctness

- Incremental-cache fingerprints now include semantic lint options and source-derived semantic context instead of relying only on rule implementation and per-file content hashes.
- Project-level semantic changes such as new `SecurityFilterChain` beans, global cache key-generator conventions, composed annotation updates, source-root composition changes, and type-resolution context changes now invalidate cache reuse safely.
- Cache loading now checks metadata first and returns early on fingerprint mismatch instead of parsing the full cache file unnecessarily.

### Cache Observability

- JSON reports now include `runtimeMetrics.cacheMissReasons`.
- HTML reports now surface cache miss reasons in the runtime metrics section.
- Maven plugin logs now print human-readable cache miss explanations before falling back to generic cache hints.

### Selective Fingerprint Scope

- Annotation, type-resolution, and auto-detect fingerprint inputs are now built lazily.
- Type-resolution context is only fingerprinted when the active rule set requires it.
- Auto-detect context is only fingerprinted when centralized-security or project-wide key-generator auto-detection is enabled.

### Benchmark Workflow

- Added `scripts/benchmark-cache.ps1` to run repeatable cold/warm cache benchmarks.
- Added `docs/PERFORMANCE_BENCHMARKING.md` with the maintainer workflow, supported targets, output interpretation, and expected artifacts.
- The benchmark script now writes machine-readable and markdown summaries under `target/benchmark-cache/`.

## Verification

The following checks were run during this iteration:

- `./mvnw.cmd -q verify`
- `cmd /c .\\mvnw.cmd -q -pl linter-core "-Dtest=AnalysisCacheStoreTest,ProjectLinterTest,ReportWriterTest" test`
- `cmd /c .\\mvnw.cmd -q -pl linter-maven-plugin -am "-Dtest=CorrectnessLintMojoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `powershell -ExecutionPolicy Bypass -File scripts\\benchmark-cache.ps1 -Targets reactor -WarmRuns 1`

## Benchmark Snapshot

The latest sample benchmark run produced:

- `reactor-sample` cold run: `WallClockMs=4920`, `ReportedTotalMs=259`, `CachedFileCount=0`, `CacheMissReasons=cache-files-missing`
- `reactor-sample` warm run: `WallClockMs=3581`, `ReportedTotalMs=216`, `CachedFileCount=4`, `CacheHitRatePercent=100`

## Current Status

- The cache invalidation model is now substantially safer and easier to inspect.
- JSON, HTML, and Maven log outputs now agree on why cache reuse was skipped.
- Maintainers now have a repeatable sample-based benchmark entrypoint for future cache and runtime work.
- The `v0.1.3` tag has now been published successfully.
- The GitHub Release for `v0.1.3` is live.
- The `Release` and matching `CI` workflows for the `v0.1.3` release commit completed successfully.
- The direct Maven Central artifact URL for `0.1.3` is available; `search.maven.org` visibility may still lag temporarily after publication.

## Remaining Caution

- Small sample-project wall-clock timings are still dominated by Maven startup and project build overhead, so benchmark trends matter more than absolute numbers.
- The current benchmark workflow is intentionally sample-driven; it is useful for regression tracking, not as a substitute for large-repository profiling.
