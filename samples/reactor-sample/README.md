# reactor-sample

This sample demonstrates multi-module scanning with `spring-correctness-linter`.

## Usage

1. Install the current plugin from the repository root:
   - Windows: `mvnw.cmd -q -DskipTests install`
   - macOS / Linux: `./mvnw -q -DskipTests install`
2. Run verification for the whole reactor:
   - `mvn -q -f samples/reactor-sample/pom.xml -DskipTests verify`
3. Inspect generated reports in the reactor root:
   - `samples/reactor-sample/target/spring-correctness-linter/lint-report.json`
   - `samples/reactor-sample/target/spring-correctness-linter/lint-report.html`
   - `samples/reactor-sample/target/spring-correctness-linter/baseline-diff.html`
   - `samples/reactor-sample/target/spring-correctness-linter/rules-reference.md`
   - `samples/reactor-sample/target/spring-correctness-linter/rules-governance.json`

The reactor report includes runtime metrics, per-module cache hit rates, analyzed vs cached timing, and a slowest-modules summary, which makes it easier
to confirm cache reuse and identify slower modules.

## Recommended bundles

Use this sample when you want to validate bundle behavior in a multi-module reactor instead of a single module:

- `CI Starter`: good for checking that domain-based selection still behaves correctly when reports are grouped by module
- `ASYNC`-heavy validation: this sample intentionally contains async issues in different modules, so it is useful for confirming module grouping, cache reuse, and per-module outputs with a narrower active rule surface

For `Transaction Focus`, `Web/API Focus`, or configuration-oriented checks, `samples/vulnerable-sample/` is the better first stop because it contains richer single-module examples for those domains.

Example commands:

- `mvn -q -f samples/reactor-sample/pom.xml -DskipTests verify "-Dspring.correctness.linter.enabledRuleDomains=ASYNC,TRANSACTION,WEB"`
- `mvn -q -f samples/reactor-sample/pom.xml -DskipTests verify "-Dspring.correctness.linter.enabledRuleDomains=ASYNC"`
- `mvn -q -f samples/reactor-sample/pom.xml -DskipTests verify "-Dspring.correctness.linter.formats=json" "-Dspring.correctness.linter.lightweightReports=true"`
- `mvn -q -f samples/reactor-sample/pom.xml -DskipTests verify "-Dspring.correctness.linter.fileAnalysisParallelism=2"`

## Module Output

- Per-module baseline files: `samples/reactor-sample/modules/<module>/spring-correctness-linter-baseline.txt`
- Per-module caches: `samples/reactor-sample/target/spring-correctness-linter/modules/<module>/analysis-cache.txt`

The parent reactor report groups findings by module, and child modules are skipped automatically when `scanReactorModules=true`.

Run the same verification command twice to see per-module cache hits increase in the reactor root `lint-report.json`.

## Expected Findings

On the default configuration, this sample is expected to produce 4 visible findings:

- `module-a`
  - `SPRING_ASYNC_VOID` from `ModuleAsyncService.runAsync()`
  - `SPRING_ASYNC_PRIVATE_METHOD` from `ModuleAsyncService.runAsync()`
- `root-app`
  - `SPRING_ASYNC_VOID` from `RootAsyncService.runAsync()`
  - `SPRING_TX_SELF_INVOCATION` from `RootTransactionalService.run(...)` calling inherited transactional method `process(...)`

This is intentional. The sample exists to confirm that:

- findings are grouped by module correctly
- cross-module inherited transactional self-invocation is still detected
- async findings in different modules are aggregated into one reactor report

## Report demo

- Governance-oriented output:
  - `mvn -q -f samples/reactor-sample/pom.xml -DskipTests verify`
  - Inspect `samples/reactor-sample/target/spring-correctness-linter/rules-governance.json`
- Lightweight JSON output:
  - `mvn -q -f samples/reactor-sample/pom.xml -DskipTests verify "-Dspring.correctness.linter.formats=json" "-Dspring.correctness.linter.lightweightReports=true"`
  - Inspect `samples/reactor-sample/target/spring-correctness-linter/lint-report.json`
- Parallelism tuning:
  - `mvn -q -f samples/reactor-sample/pom.xml -DskipTests verify "-Dspring.correctness.linter.parallelFileAnalysis=false"`
  - `mvn -q -f samples/reactor-sample/pom.xml -DskipTests verify "-Dspring.correctness.linter.fileAnalysisParallelism=2"`
