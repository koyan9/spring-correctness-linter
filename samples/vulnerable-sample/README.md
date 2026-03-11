# vulnerable-sample

This sample intentionally contains several Spring design and correctness issues to demonstrate `spring-correctness-linter` output.

## Usage

1. Install the current plugin from the repository root:
   - Windows: `mvnw.cmd -q -DskipTests install`
   - macOS / Linux: `./mvnw -q -DskipTests install`
2. Run verification in this directory:
   - `mvn -q verify`
3. Inspect generated reports:
   - `target/spring-correctness-linter/lint-report.json`
   - `target/spring-correctness-linter/lint-report.html`
   - `target/spring-correctness-linter/lint-report.sarif.json`
   - `target/spring-correctness-linter/baseline-diff.html` (when baseline diff output is enabled)

The JSON and HTML reports also expose runtime metrics such as analyzed files, cached files, cache hit rate, phase timings,
and slow-module summaries when multiple modules are scanned.

## Recommended bundles

This sample is the best fit when you want to validate domain bundles that focus on single-module runtime semantics:

- `CI Starter`: good for a broad first pass across async, transaction, and web-facing issues in one module
- `Scheduled Focus`: covers missing or conflicting `@Scheduled` trigger configuration, repeated schedules, non-positive intervals, scheduled method parameters, non-void scheduled return values, and `@Scheduled` + `@Async` / `@Transactional` boundary reviews
- `Lifecycle Focus`: covers `@PostConstruct`, `afterPropertiesSet()`, `ApplicationRunner`, and `SmartInitializingSingleton` callbacks combined with `@Async` or `@Transactional`
- `Transaction Focus`: covers self-invocation, private/final transactional methods, high-risk propagation, and event/transaction boundary combinations
- `Web/API Focus`: covers controller profile drift and public endpoint security intent

It also includes a configuration conflict example in `ConditionalConfig.java`, so it is useful when you want to spot-check `CONFIGURATION` findings alongside the recommended bundles.

Example commands:

- `mvn -q verify "-Dspring.correctness.linter.enabledRuleDomains=ASYNC,TRANSACTION,WEB"`
- `mvn -q verify "-Dspring.correctness.linter.enabledRuleDomains=SCHEDULED"`
- `mvn -q verify "-Dspring.correctness.linter.enabledRuleDomains=LIFECYCLE"`
- `mvn -q verify "-Dspring.correctness.linter.enabledRuleDomains=TRANSACTION,EVENTS"`
- `mvn -q verify "-Dspring.correctness.linter.enabledRuleDomains=WEB"`

## Baseline demo

- Generate a baseline: `mvn -q spring-correctness-linter:lint -Dspring.correctness.linter.writeBaseline=true`
- Default baseline file: `spring-correctness-linter-baseline.txt`
- Run `mvn -q verify` again to keep only issues not already recorded in the baseline.
- Re-run `mvn -q verify` without changing sources to observe cache reuse in `lint-report.json`.
