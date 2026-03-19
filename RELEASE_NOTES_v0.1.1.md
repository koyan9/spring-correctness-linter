# spring-correctness-linter v0.1.1

## Highlights

- Expanded Spring correctness coverage around async and transactional proxy-boundary pitfalls
- Improved cache-key accuracy with stronger composed-annotation support and opt-in project-wide key-generator detection
- Added governance-oriented outputs and lightweight JSON reporting for large repositories
- Added safer, configurable file-analysis concurrency instead of relying on the common fork-join pool
- Improved adoption guidance, samples, and rule-governance documentation

## Included Rule Areas

- Added:
  - `SPRING_ASYNC_FINAL_METHOD`
  - `SPRING_TX_FINAL_CLASS`
- Expanded:
  - `SPRING_ASYNC_SELF_INVOCATION` now flags direct self method references such as `this::method`
  - `SPRING_TX_SELF_INVOCATION` now flags direct self method references such as `this::method`
  - `SPRING_ENDPOINT_SECURITY` now honors inherited security intent from interfaces and base classes
  - `SPRING_ENDPOINT_SECURITY` supports opt-in centralized-security auto-detection for both `SecurityFilterChain` and `SecurityWebFilterChain`
  - `SPRING_CACHEABLE_KEY` now treats blank `key` / `keyGenerator` values as missing
  - `SPRING_CACHEABLE_KEY` now performs exact cache-name allowlist matching and covers composed `@CacheConfig` usage
  - `SPRING_CACHEABLE_KEY` supports opt-in detection of project-wide key conventions through `@Bean KeyGenerator`, `CachingConfigurer`, and `CachingConfigurerSupport`
- Removed or deprecated:
  - None

## Tooling

- Maven plugin changes:
  - Added `moduleSourceDirectories` for per-module extra source roots in reactor scans
  - Added `parallelFileAnalysis` and `fileAnalysisParallelism` to control file-analysis concurrency
  - Added `lightweightReports` for smaller JSON output
  - Added `autoDetectCentralizedSecurity` and `autoDetectProjectWideKeyGenerator` for explicit opt-in convention detection
  - Unknown report formats now warn instead of failing silently
  - GitHub Actions release automation now imports Central credentials and GPG signing material, publishes to Maven Central, and only then creates the GitHub release
- Report generation changes:
  - Added `rules-governance.json`
  - Added lightweight JSON output mode
  - Added cache-miss diagnostic hints in Maven logs
- Sample project changes:
  - Sample READMEs now demonstrate governance output, lightweight JSON, and concurrency options
  - Vulnerable sample includes new async/transactional proxy-boundary examples

## Breaking Changes

- None

## Reports and CI

- JSON / HTML report changes:
  - Governance-oriented output now includes `rules-governance.json`
  - Lightweight JSON mode keeps summary and rule-selection data while omitting heavier finding sections
  - Report writer regression coverage now locks key output fields to reduce accidental format drift
- SARIF or code scanning changes:
  - SARIF output continues to include module identifiers for multi-module repositories
- Baseline / baseline diff changes:
  - Baseline and analysis-cache loading now stream files to reduce memory pressure on large repositories
- Multi-module, reactor, or cache behavior changes:
  - Reactor scans support module-specific extra source roots
  - Cache miss logs now explain likely reuse failures
  - File analysis concurrency is explicitly configurable and no longer depends on the common fork-join pool

## Upgrade and Adoption Notes

- Recommended Maven configuration changes:
  - Consider enabling `lightweightReports=true` when only machine-readable summaries are needed
  - Use `parallelFileAnalysis` and `fileAnalysisParallelism` to control analysis CPU usage in CI
  - Use `autoDetectCentralizedSecurity=true` or `autoDetectProjectWideKeyGenerator=true` only when those conventions are genuinely present
- New or renamed Maven properties:
  - `spring.correctness.linter.moduleSourceDirectories`
  - `spring.correctness.linter.parallelFileAnalysis`
  - `spring.correctness.linter.fileAnalysisParallelism`
  - `spring.correctness.linter.lightweightReports`
  - `spring.correctness.linter.autoDetectCentralizedSecurity`
  - `spring.correctness.linter.autoDetectProjectWideKeyGenerator`
- Maven Central publication:
  - The repository now includes a `central-publish` Maven profile for local Sonatype Central publication with sources, javadocs, and GPG signing
  - Local `settings.xml` examples now use environment-backed credentials and passphrases instead of plaintext token values
- Cache invalidation or analysis fingerprint notes:
  - Incremental cache reuse is still invalidated automatically when effective rule configuration or implementation changes
- PowerShell, sample, or CI workflow notes:
  - Sample READMEs now include direct commands for governance output, lightweight JSON, and concurrency tuning
  - PowerShell users should continue quoting dotted `-Dspring.correctness.linter.*` properties or invoking Maven through `cmd /c`

## Verification

- `./mvnw -q verify`
- `./mvnw -q -pl linter-core -am test`
- `./mvnw -q -pl linter-maven-plugin -am test`
- `./mvnw -q -f samples/vulnerable-sample/pom.xml -DskipTests verify`
- `./mvnw -q -f samples/reactor-sample/pom.xml -DskipTests verify`
- `./mvnw -q -f samples/adoption-suite/pom.xml -DskipTests verify`
