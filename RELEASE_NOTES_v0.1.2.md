# spring-correctness-linter v0.1.2

## Highlights

- Hardened reactor/module output behavior so aggregator-only or Java-empty source roots no longer produce module artifacts
- Expanded regression coverage around Maven plugin configuration contracts and Spring correctness rule boundaries
- Aligned CI, samples, and bilingual documentation with the current adoption and governance workflow

## Included Rule Areas

- Added:
  - None
- Expanded:
  - `SPRING_ENDPOINT_SECURITY` regression coverage now includes inherited custom security intent and inherited composed method-security intent
  - `SPRING_CACHEABLE_KEY` regression coverage now includes blank `key` handling, zero-argument cacheables, composed type-level `@CacheConfig(keyGenerator=...)`, and composed single-member allowlists
- Removed or deprecated:
  - None

## Tooling

- Maven plugin changes:
  - Reactor/source-root normalization now excludes nonexistent or Java-empty roots from module reports and per-module baseline/cache outputs
  - Non-execution-root modules continue to short-circuit cleanly when `scanReactorModules=true`
  - Plugin regression coverage now locks behavior for `failOnError`, `failOnSeverity`, `ruleDocsFileName`, mixed valid/invalid formats, `includeTestSourceRoots`, `moduleSourceDirectories`, and enabled/disabled rule selection conflicts
- Report generation changes:
  - Governance and rule-doc outputs are now covered more explicitly when core report formats are disabled or partially invalid
  - Baseline diff generation is now covered independently from core report-format selection
- Sample project changes:
  - `samples/adoption-suite/` is now validated explicitly in CI
  - Reactor and adoption sample READMEs now describe the current module-filtering and rollout behavior more precisely

## Breaking Changes

- None

## Reports and CI

- JSON / HTML report changes:
  - No wire-format redesign, but plugin regression tests now lock more output-path and output-selection behavior
- SARIF or code scanning changes:
  - No functional SARIF schema change in this release
- Baseline / baseline diff changes:
  - Per-module baseline/cache outputs are no longer emitted for aggregator-only modules that do not contribute Java source files
  - Baseline diff generation remains available even when all configured core report formats are invalid
- Multi-module, reactor, or cache behavior changes:
  - Reactor scans now report only modules that actually contribute Java source files
  - Cache-key accuracy regressions now protect more composed-annotation and convention-driven scenarios

## Upgrade and Adoption Notes

- Recommended Maven configuration changes:
  - No new Maven properties were introduced in this release
  - Teams using reactor scans should expect cleaner module output when the execution root is only an aggregator
- New or renamed Maven properties:
  - None
- Cache invalidation or analysis fingerprint notes:
  - Incremental cache behavior is unchanged; cache reuse still invalidates automatically when the effective analysis fingerprint changes
- PowerShell, sample, or CI workflow notes:
  - `samples/adoption-suite/` is now part of the default CI verification path
  - Documentation now consistently explains that `writeRuleDocs=false` also disables `rules-governance.json`
  - Documentation now consistently explains that `securityAnnotations` are matched by normalized simple names rather than package-qualified identity

## Verification

- `./mvnw.cmd -q -pl linter-core -Dtest=ProjectLinterTest test`
- `./mvnw.cmd -q -pl linter-maven-plugin -Dtest=CorrectnessLintMojoTest test`
- `./mvnw.cmd -q verify`
- `cmd /c mvn -q -f samples\\reactor-sample\\pom.xml clean verify -DskipTests`
- `cmd /c mvn -q -f samples\\adoption-suite\\pom.xml -DskipTests verify`
