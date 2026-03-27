# CI Examples

This document provides copy-paste-ready CI examples for common `spring-correctness-linter`
adoption workflows.

Use these examples when you want a ready baseline for:

- severity-based quality gates
- baseline-first rollout on legacy codebases
- SARIF upload to GitHub code scanning

## 1. GitHub Actions Quality Gate

Use this when you already have a committed baseline and want CI to fail on new visible findings
 at or above `WARNING`.

```yaml
name: Verify

on:
  pull_request:
  push:
    branches: [main]

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Cache linter analysis
        uses: actions/cache@v4
        with:
          path: .cache/spring-correctness-linter
          key: ${{ runner.os }}-linter-${{ hashFiles('**/pom.xml') }}

      - name: Verify with quality gate
        run: >
          mvn -B -q verify
          "-Dspring.correctness.linter.applyBaseline=true"
          "-Dspring.correctness.linter.failOnSeverity=WARNING"
          "-Dspring.correctness.linter.cacheFile=.cache/spring-correctness-linter/analysis-cache.txt"
```

Notes:

- `failOnSeverity` takes precedence over `failOnError`
- keep `applyBaseline=true` after the first baseline is committed
- inspect `runtimeMetrics.cacheMissReasons` when cache reuse does not behave as expected

## 2. Baseline-First Rollout

Use this when the project already has historical findings and the first goal is to stop regressions,
not to clean up all legacy issues at once.

### One-time baseline generation

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.4:lint \
  "-Dspring.correctness.linter.writeBaseline=true"
```

For reactor projects:

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.4:lint \
  "-Dspring.correctness.linter.scanReactorModules=true" \
  "-Dspring.correctness.linter.splitBaselineByModule=true"
```

Commit the generated baseline file or module baselines before enabling CI filtering.

### Report-only CI during rollout

```yaml
name: Verify Without Gate

on:
  pull_request:

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Verify with baseline applied
        run: >
          mvn -B -q verify
          "-Dspring.correctness.linter.applyBaseline=true"
```

Recommended rollout path:

1. Generate and commit the baseline
2. Run CI with `applyBaseline=true` and no failure threshold
3. Review `baseline-diff.json` / `baseline-diff.html`
4. Add `failOnSeverity` only after the team is comfortable with the visible findings

## 3. GitHub Code Scanning with SARIF

Use this when you want `spring-correctness-linter` findings to appear in GitHub code scanning.

```yaml
name: Code Scanning

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read
  security-events: write

jobs:
  sarif:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Verify with SARIF output
        run: >
          mvn -B -q verify
          "-Dspring.correctness.linter.formats=sarif"

      - name: Upload SARIF
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: target/spring-correctness-linter/lint-report.sarif.json
```

For reactor scans from the execution root:

- keep SARIF upload pointed at the reactor root report directory
- use the root SARIF file produced by the execution-root module
- note that SARIF results also include `moduleId` for module-aware grouping

## 4. Troubleshooting Checklist

If the workflow succeeds but cache reuse looks wrong:

- inspect `lint-report.json` and check `runtimeMetrics.cacheMissReasons`
- compare cold and warm runs with `scripts/benchmark-cache.ps1`
- confirm the cache path stays stable across CI runs
- confirm source-root composition did not change unintentionally

If code scanning upload succeeds but you do not see findings immediately:

- confirm SARIF output is enabled
- confirm the uploaded path matches the generated file
- confirm the workflow has `security-events: write`

## 5. Related Docs

- [quick-start.md](../quick-start.md)
- [README.md](../README.md)
- [docs/PERFORMANCE_BENCHMARKING.md](PERFORMANCE_BENCHMARKING.md)
- [docs/RELEASE_PROCESS.md](RELEASE_PROCESS.md)
