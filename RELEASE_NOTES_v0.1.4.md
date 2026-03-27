# spring-correctness-linter v0.1.4

## Highlights

- Added a lightweight external rule extension path through `ServiceLoader`, including an adoption-suite sample and plugin-side validation coverage
- Expanded low-noise Spring proxy-boundary coverage across async, cache, and transactional event-listener scenarios
- Tightened project-level auto-detection accuracy for both centralized security and project-wide cache key-generator conventions, including source-local implementation classes

## Included Rule Areas

- Added:
  - `SPRING_ASYNC_UNSUPPORTED_RETURN_TYPE`
  - `SPRING_ASYNC_FINAL_CLASS`
  - `SPRING_CACHEABLE_PRIVATE_METHOD`
  - `SPRING_CACHEABLE_FINAL_METHOD`
  - `SPRING_CACHEABLE_FINAL_CLASS`
  - `SPRING_CACHEABLE_SELF_INVOCATION`
  - `SPRING_TRANSACTIONAL_EVENT_LISTENER_ASYNC`
- Expanded:
  - `SPRING_CACHEABLE_KEY` project-wide key-generator auto-detection now also recognizes `@Bean` methods that return source-local `KeyGenerator` implementation classes
  - `SPRING_ENDPOINT_SECURITY` centralized-security auto-detection now also recognizes `@Bean` methods that return source-local `SecurityFilterChain` implementation classes
- Removed or deprecated:
  - None

## Tooling

- Maven plugin changes:
  - External rules discovered through `ServiceLoader` are now visible in the normal Maven plugin execution path
  - Plugin regression coverage now locks in external-rule discovery, generated rule-reference snapshots, and the new centralized-security / key-generator auto-detection cases
- Report generation changes:
  - Generated `rules-reference.md` now includes a domain coverage snapshot with per-domain rule counts and a compact proxy-boundary vs advisory matrix
  - Lightweight JSON output continues to preserve compact runtime summaries while governance-oriented docs stay aligned with the current built-in rule surface
- Sample project changes:
  - Added `samples/adoption-suite/external-rules-app/` to demonstrate consumer-style external rule packaging and discovery
  - `samples/vulnerable-sample/` now demonstrates the newly added async, cache, and event-boundary findings directly

## Breaking Changes

- None

## Reports and CI

- JSON / HTML report changes:
  - No schema break in the core report formats for this release candidate
  - Generated rule-reference docs now expose a clearer domain coverage snapshot for rollout planning
- SARIF or code scanning changes:
  - No SARIF schema change in this release candidate
- Baseline / baseline diff changes:
  - No new baseline file format change in this release candidate
- Multi-module, reactor, or cache behavior changes:
  - Auto-detect context remains part of the analysis fingerprint, and now also responds to source-local `KeyGenerator` and `SecurityFilterChain` implementation-class changes
  - Incremental-cache invalidation regression coverage was extended so those project-level semantic changes do not silently reuse stale findings
- Performance or cache evidence:
  - No dedicated benchmark run was captured for this candidate because the changes were centered on correctness, extensibility, and documentation rather than runtime-sensitive performance paths

## Upgrade and Adoption Notes

- Recommended Maven configuration changes:
  - No new Maven properties are required for this release candidate
  - Teams that want to extend the built-in rule set can now package additional rules through the `io.github.koyan9.linter.core.spi.LintRuleProvider` SPI without modifying the built-in registry
- New or renamed Maven properties:
  - None
- Cache invalidation or analysis fingerprint notes:
  - `SPRING_CACHEABLE_KEY` project-wide key-generator auto-detection now also tracks source-local implementation classes, not just direct `KeyGenerator` interface return types
  - `SPRING_ENDPOINT_SECURITY` centralized-security auto-detection now also tracks source-local `SecurityFilterChain` implementation classes returned from `@Bean` methods
- PowerShell, sample, or CI workflow notes:
  - Release hygiene docs now include post-release verification guidance and a repeatable `scripts/check-release-status.ps1` entrypoint
  - Copy-paste CI examples now cover baseline rollout, quality gates, and SARIF upload flows more directly

## Verification

- `./mvnw.cmd -q verify`
- `./mvnw.cmd -q -pl linter-core -am test`
- `./mvnw.cmd -q -pl linter-maven-plugin -am test`
- `cmd /c mvn -q -f samples\\vulnerable-sample\\pom.xml -DskipTests verify`
- `cmd /c mvn -q -f samples\\reactor-sample\\pom.xml -DskipTests verify`
- `cmd /c mvn -q -f samples\\adoption-suite\\pom.xml -DskipTests verify`
