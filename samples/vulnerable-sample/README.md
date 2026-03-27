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
   - `target/spring-correctness-linter/rules-reference.md`
   - `target/spring-correctness-linter/rules-governance.json`

The JSON and HTML reports also expose runtime metrics such as analyzed files, cached files, cache hit rate, phase timings,
and slow-module summaries when multiple modules are scanned.

The governance snapshot is especially useful when you want to verify which rule ids, domains, and severities are actually active in the current run.

## Expected Findings

This sample is intentionally noisy. Under the default rule set, visible findings are expected.

Typical findings include:

- `SPRING_ENDPOINT_SECURITY` from `DemoController.openEndpoint()`
- `SPRING_PROFILE_CONTROLLER` from the controller-level `@Profile`
- `SPRING_ASYNC_VOID`, `SPRING_ASYNC_UNSUPPORTED_RETURN_TYPE`, `SPRING_ASYNC_PRIVATE_METHOD`, and `SPRING_ASYNC_FINAL_METHOD` from the async methods in `DemoService`
- `SPRING_CACHEABLE_PRIVATE_METHOD` from `DemoService.privateCachedValue()`
- `SPRING_CACHEABLE_FINAL_METHOD` from `DemoService.finalCachedValue()`
- `SPRING_CACHEABLE_SELF_INVOCATION` from `DemoService.warmCache()`
- `SPRING_ASYNC_FINAL_CLASS` from `FinalAsyncService`
- `SPRING_TX_SELF_INVOCATION`, `SPRING_TX_PRIVATE_METHOD`, `SPRING_TX_FINAL_METHOD`, and `SPRING_TX_HIGH_RISK_PROPAGATION` from transactional patterns in `DemoService`
- `SPRING_TX_FINAL_CLASS` from `FinalTransactionalService`
- `SPRING_EVENT_LISTENER_TRANSACTIONAL` and `SPRING_TRANSACTIONAL_EVENT_LISTENER` from event listener boundaries
- `SPRING_SCHEDULED_*` findings from intentionally conflicting or unsafe scheduling definitions
- `SPRING_LIFECYCLE_*` and `SPRING_STARTUP_*` findings from lifecycle / startup callbacks
- `SPRING_CONDITIONAL_BEAN_CONFLICT` from `ConditionalConfig`

Some of these are deterministic proxy-boundary mistakes. Others are advisory Spring runtime-risk reviews by design. This sample exists to demonstrate both kinds.

## Recommended bundles

This sample is the best fit when you want to validate domain bundles that focus on single-module runtime semantics:

- `CI Starter`: good for a broad first pass across async, transaction, and web-facing issues in one module
- `Scheduled Focus`: covers missing or conflicting `@Scheduled` trigger configuration, repeated schedules, non-positive intervals, scheduled method parameters, non-void scheduled return values, and `@Scheduled` + `@Async` / `@Transactional` boundary reviews
- `Lifecycle Focus`: covers `@PostConstruct`, `afterPropertiesSet()`, `ApplicationRunner`, and `SmartInitializingSingleton` callbacks combined with `@Async` or `@Transactional`
- `Transaction Focus`: covers self-invocation, private/final transactional methods, final transactional classes, high-risk propagation, and event/transaction boundary combinations
- `Web/API Focus`: covers controller profile drift and public endpoint security intent

It also includes a configuration conflict example in `ConditionalConfig.java`, so it is useful when you want to spot-check `CONFIGURATION` findings alongside the recommended bundles.

Example commands:

- `mvn -q verify "-Dspring.correctness.linter.enabledRuleDomains=ASYNC,TRANSACTION,WEB"`
- `mvn -q verify "-Dspring.correctness.linter.enabledRuleDomains=SCHEDULED"`
- `mvn -q verify "-Dspring.correctness.linter.enabledRuleDomains=LIFECYCLE"`
- `mvn -q verify "-Dspring.correctness.linter.enabledRuleDomains=TRANSACTION,EVENTS"`
- `mvn -q verify "-Dspring.correctness.linter.enabledRuleDomains=WEB"`


## Optional configuration

- Centralized security intent:
  - `mvn -q verify "-Dspring.correctness.linter.assumeCentralizedSecurity=true"`
  - `mvn -q verify "-Dspring.correctness.linter.securityAnnotations=InternalEndpoint"`
- Allow default cache keys for selected caches:
  - `mvn -q verify "-Dspring.correctness.linter.cacheDefaultKeyCacheNames=users,orders"`
- Auto-detect a project-wide cache key strategy:
  - `mvn -q verify "-Dspring.correctness.linter.autoDetectProjectWideKeyGenerator=true"`
- Write a lightweight JSON report:
  - `mvn -q verify "-Dspring.correctness.linter.formats=json" "-Dspring.correctness.linter.lightweightReports=true"`
- Control file-analysis concurrency explicitly:
  - `mvn -q verify "-Dspring.correctness.linter.parallelFileAnalysis=false"`
  - `mvn -q verify "-Dspring.correctness.linter.fileAnalysisParallelism=4"`

## Report demo

- Governance-oriented output:
  - `mvn -q verify`
  - Inspect `target/spring-correctness-linter/rules-governance.json`
- Lightweight JSON output:
  - `mvn -q verify "-Dspring.correctness.linter.formats=json" "-Dspring.correctness.linter.lightweightReports=true"`
  - Inspect `target/spring-correctness-linter/lint-report.json`
  - This mode keeps the summary and rule-selection sections, but omits the heavier findings and runtime detail sections.


## Centralized security example

If the sample is used in an environment where security is enforced in a gateway or shared `SecurityFilterChain`, you can run:

- `mvn -q verify "-Dspring.correctness.linter.assumeCentralizedSecurity=true"`
- `mvn -q verify "-Dspring.correctness.linter.securityAnnotations=InternalEndpoint"`

## Baseline demo

- Generate a baseline: `mvn -q spring-correctness-linter:lint -Dspring.correctness.linter.writeBaseline=true`
- Default baseline file: `spring-correctness-linter-baseline.txt`
- Run `mvn -q verify` again to keep only issues not already recorded in the baseline.
- Re-run `mvn -q verify` without changing sources to observe cache reuse in `lint-report.json`.
- Inspect `target/spring-correctness-linter/baseline-diff.html` or `baseline-diff.json` to see new vs matched vs stale entries.
