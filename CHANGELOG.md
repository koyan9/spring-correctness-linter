# Changelog

All notable changes to `spring-correctness-linter` will be documented in this file.

## [Unreleased]

### Added

- Core regression coverage now includes incremental-cache invalidation when centralized-security detection, project-wide key-generator detection, composed annotation definitions, type-resolution context, or custom security-annotation options change
- Plugin regression coverage now includes incremental-cache invalidation for `autoDetectCentralizedSecurity`, `includeTestSourceRoots`, and stale baseline-module ownership with split baselines
- Runtime metrics and plugin logs now include cache miss reasons so users can distinguish missing cache files, rule/config changes, source-root changes, annotation/type-context changes, auto-detect-context changes, and file-content churn
- Added `scripts/benchmark-cache.ps1` and `docs/PERFORMANCE_BENCHMARKING.md` to make cold/warm cache benchmarking repeatable across the reactor and adoption samples

### Changed

- Release workflow now waits for Sonatype Central validation instead of full published propagation before creating the GitHub release, reducing timeout risk after artifacts are already accepted
- Release workflow reruns now detect when the tagged version is already visible in Maven Central and skip duplicate deploy attempts
- Incremental-cache fingerprints now include semantic lint options and source-derived semantic context, so cache reuse is invalidated when project semantics change even if individual files do not
- Auto-detection for centralized security and project-wide cache key generators now relies on resolvable Spring types instead of simple-name-only matches
- Baseline diff now assigns stale entries to modules using module-scoped baseline ownership first and longest source-root path matching as the shared-baseline fallback
- README and quick-start guides now document the stricter Spring-type auto-detection behavior and the broader incremental-cache invalidation semantics

### Fixed

- `SPRING_ENDPOINT_SECURITY` and `SPRING_CACHEABLE_KEY` no longer silently reuse stale incremental-cache findings after project-level semantic changes such as new Spring security-chain beans, project-wide key generators, or composed annotation updates
- Centralized-security and project-wide key-generator auto-detection no longer misfire on unrelated application types that only share Spring simple names
- Stale baseline entries from non-standard or additional source roots now remain attributed to the correct module in module-aware baseline diff output
- `SPRING_ENDPOINT_SECURITY` no longer treats inherited secured overloads with the same method name and arity but different parameter types as implicitly protected endpoints
- `SPRING_CACHEABLE_KEY` now recognizes `@Caching(cacheable = @Cacheable(...))` declarations for explicit key-strategy checks and default-key allowlists

## [0.1.2] - 2026-03-26

### Added

- CI now validates `samples/adoption-suite/` alongside the vulnerable and reactor samples
- Plugin regression coverage now includes `failOnError`, `failOnSeverity` precedence, invalid severity values, mixed valid/invalid report formats, and custom `ruleDocsFileName` handling
- Plugin regression coverage now includes `includeTestSourceRoots` and `moduleSourceDirectories` validation paths for multi-root and multi-module configuration
- Core regression coverage now includes inherited custom/composed security intent scenarios and additional cache-key convention boundaries

### Changed

- Reactor and source-root normalization now exclude nonexistent or Java-empty roots from module reports and per-module baseline/cache outputs
- Documentation and sample guides now consistently explain `rules-governance.json`, `securityAnnotations` normalization, `GENERAL` domain semantics, and reactor filtering behavior
- Adoption-oriented sample guidance now reflects explicit CI validation and clearer consumer rollout positioning

## [0.1.1] - 2026-03-18

### Added

- Multi-source-root and Maven reactor module scanning support
- Module summaries in JSON / HTML reports and baseline diff output
- `baseline-diff.html` report generation
- Optional per-module baseline and incremental cache output
- Rule enable / disable selection and per-rule severity overrides
- Configuration for centralized endpoint security and custom security annotations
- Configuration to allow default cache keys for selected cache names
- Reactor sample project for multi-module validation
- Runtime metrics in JSON / HTML reports, including phase timing, cache hit rate, and per-module analysis timing
- Runtime summary logging in the Maven plugin for analyzed vs cached file counts
- Recommended Maven configuration templates and GitHub SARIF upload guidance in the documentation
- Added async self-invocation detection for @Async proxy bypass scenarios
- Added async transactional boundary detection for @Async + @Transactional combinations
- Added `moduleSourceDirectories` to configure per-module extra source roots
- Added async final-method detection for @Async proxy boundaries
- Added final-class detection for @Transactional proxy boundaries

### Changed

- Rule reference markdown now includes configuration and suppression examples
- Analysis pipeline now caches unchanged files and surfaces cache reuse in reports and logs
- HTML report now includes module dashboards and grouped issue presentation
- HTML report findings now link to in-page rule guidance with applicability, false-positive boundaries, and remediation notes
- JSON reports now expose shared `ruleGuidance` summaries for frontends and downstream tooling
- Runtime metrics now include per-module cache hit rates and slow-module summaries in JSON and HTML reports
- Built-in Spring rules are now registered through a lightweight internal registry instead of a single hard-coded list literal
- Built-in rules now expose lightweight domain groups such as async, transaction, cache, web, events, and configuration
- Rules can now be enabled or disabled by domain through Maven configuration
- HTML and JSON reports now surface configured and effective rule-domain selections for easier CI debugging
- HTML and JSON reports now also show configured rule ids and effective per-domain rule breakdowns
- Maven plugin logs now print a concise rule-selection summary for faster CI diagnosis
- Maven plugin logs now include cache hit rate and slow-module summaries for multi-module scans
- Slow-module summaries now focus on analyzed time to avoid cache-only skew
- Scheduled trigger configuration checks now treat placeholder values as configured to reduce false positives
- Baseline and incremental cache loaders now stream files to reduce memory usage on large repositories
- Maven plugin logs now include cache-miss hints when incremental cache is enabled but no entries were reused
- Rule-doc generation now also outputs `rules-governance.json` for governance/audit workflows
- SARIF output now includes module identifiers for multi-module repositories
- Analysis now processes source files in parallel when multiple files are present to improve throughput
- Added `lightweightReports` to generate smaller JSON outputs for large repositories
- Cacheable key strategy now ignores blank `key`/`keyGenerator` values to reduce false negatives
- Cacheable default-key allowlist now honors `@CacheConfig(cacheNames=...)`
- Endpoint security rule now honors security annotations declared on interfaces or base classes
- Transactional self-invocation now flags direct self method references such as `this::method`
- Added auto-detection for centralized security when a `SecurityFilterChain` bean is present
- Cacheable allowlist tests now cover composed cache names and composed `@CacheConfig` usage
- Async self-invocation now flags direct self method references such as `this::method`
- Added configurable file-analysis parallelism instead of relying on the common fork-join pool
- Cache default-key allowlist matching is now exact, avoiding substring-based false negatives
- Centralized security auto-detection now also recognizes reactive `SecurityWebFilterChain` beans
- Added opt-in auto-detection for project-wide `KeyGenerator` beans to reduce cache-key noise
- Project-wide cache key auto-detection now also recognizes `CachingConfigurer` / `CachingConfigurerSupport`
- Annotation meta-resolution now prefers same-package and import-based matches when simple names are ambiguous
- Generated rule reference docs now include recommended domain-based rule bundles for common rollout scenarios
- Added an initial `SCHEDULED` rule domain covering invalid trigger configuration, scheduled method parameters, and non-void return-value review
- Expanded `SCHEDULED` coverage with `@Scheduled` + `@Async` and `@Scheduled` + `@Transactional` boundary checks
- Expanded `SCHEDULED` coverage with repeated-schedule and non-positive-interval checks
- Added an initial `LIFECYCLE` rule domain covering initialization callbacks combined with `@Async` and `@Transactional`
- Expanded `LIFECYCLE` coverage to startup callbacks such as `ApplicationRunner` and `SmartInitializingSingleton`
- `samples/vulnerable-sample/` now includes vulnerable `@Scheduled` examples for end-to-end validation
- Quality gate failures now report the affected module IDs
- Incremental cache reuse is now documented with explicit fingerprint-based invalidation semantics
- `README.zh-CN.md` is aligned with the English README for runtime metrics, cache guidance, and code scanning integration
- Analysis now reuses annotation parse results and avoids redundant hashing to improve end-to-end scan performance
- Release workflow now checks out the requested tag, publishes to Maven Central, and only then creates the GitHub release
- Release documentation now recommends environment-backed Maven Central and GPG credentials instead of plaintext settings examples

### Fixed

- `SPRING_ENDPOINT_SECURITY` now includes class-level composed security annotation coverage in tests
- `SPRING_CACHEABLE_KEY` respects class-level `@CacheConfig(keyGenerator = ...)` when determining explicit cache key strategy
- Endpoint security semantic facts now recognize `@PostAuthorize`, `@PreFilter`, and `@PostFilter` alongside existing security annotations
- `SPRING_CACHEABLE_KEY` no longer flags zero-argument `@Cacheable` methods while still warning on parameterized methods without an explicit key strategy
- `SPRING_TX_SELF_INVOCATION` now also detects unqualified same-type calls such as `inner()`, covers class-level `@Transactional` public methods, supports multi-level inheritance across source roots, includes interface default-method targets (including final classes), resolves implicit same-package and explicit/wildcard imports for nested types, supports varargs matching, skips final transactional targets already covered by `SPRING_TX_FINAL_METHOD`, and reduces overload noise by matching method arity; ambiguous simple-name inheritance resolves same-package matches first and skips cross-package mismatches
- Sample naming, report paths, and plugin property documentation are aligned with `spring-correctness-linter`
- Parse problems are surfaced instead of being silently dropped from analysis output

## [0.1.0] - 2026-03-07

### Added

- Initial AST-based Spring correctness analysis engine
- JSON / HTML / SARIF reports
- Inline suppression with reason and scope support
- Baseline and baseline diff support
- Severity-based quality gate
- Auto-generated rule reference markdown
- Maven plugin integration
- Vulnerable sample project

### Changed

- Default rule evaluation moved from regex-oriented detection toward AST-based analysis

### Fixed

- Reduced false positives caused by comments and string literals
