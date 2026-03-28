# spring-correctness-linter v0.1.5

## Highlights

- Extended low-noise project-level auto-detection so both cache-key and centralized-security conventions now recognize component-scanned Spring implementations in addition to prior bean-style signals
- Hardened release workflow guardrails around tag metadata alignment and Maven Central rerun safety
- Aligned adoption-suite consumer samples with the current component-scanned auto-detect paths

## Included Rule Areas

- Added:
  - None
- Expanded:
  - `SPRING_CACHEABLE_KEY` project-wide key-generator auto-detection now also recognizes component-scanned Spring `KeyGenerator` implementations
  - `SPRING_ENDPOINT_SECURITY` centralized-security auto-detection now also recognizes component-scanned Spring `SecurityFilterChain` / `SecurityWebFilterChain` implementations
- Removed or deprecated:
  - None

## Tooling

- Maven plugin changes:
  - No new plugin parameters were added for this release
  - Existing opt-in auto-detect flags now cover a broader but still explicit Spring source signal surface
- Report generation changes:
  - No report schema change in this release candidate
- Sample project changes:
  - `samples/adoption-suite/cache-convention-app` now demonstrates a component-scanned `KeyGenerator`
  - `samples/adoption-suite/centralized-security-app` now demonstrates a component-scanned `SecurityFilterChain`

## Breaking Changes

- None

## Reports and CI

- JSON / HTML report changes:
  - No schema break
- SARIF or code scanning changes:
  - No SARIF schema change
- Baseline / baseline diff changes:
  - No baseline format change
- Multi-module, reactor, or cache behavior changes:
  - Incremental-cache auto-detect context now invalidates correctly when component-scanned security-chain or key-generator conventions appear or disappear
- Performance or cache evidence:
  - No benchmark run was required because this batch focused on correctness boundaries, release hygiene, and sample alignment rather than runtime performance

## Upgrade and Adoption Notes

- Recommended Maven configuration changes:
  - No new configuration is required
  - Projects already using `autoDetectProjectWideKeyGenerator=true` or `autoDetectCentralizedSecurity=true` can now benefit from component-scanned Spring conventions without extra configuration
- New or renamed Maven properties:
  - None
- Cache invalidation or analysis fingerprint notes:
  - The auto-detect fingerprint now responds to component-scanned Spring `KeyGenerator`, `SecurityFilterChain`, and `SecurityWebFilterChain` implementations
- PowerShell, sample, or CI workflow notes:
  - Release workflow now fails early if the requested tag does not match the root `pom.xml` version and `scm.tag`
  - Release rerun checks now require both core and plugin artifacts to be visible in Maven Central before deploy is skipped

## Verification

- `./mvnw.cmd -q verify`
- `./mvnw.cmd -q -pl linter-core "-Dtest=ProjectLinterTest,ReleaseMetadataConsistencyTest" test`
- `./mvnw.cmd -q -pl linter-maven-plugin -am "-Dtest=CorrectnessLintMojoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `./mvnw.cmd -q -Prelease-artifacts clean verify`
- `./mvnw.cmd -q -Pcentral-publish -DskipTests verify`
- `cmd /c mvn -q -f samples\\vulnerable-sample\\pom.xml -DskipTests verify`
- `cmd /c mvn -q -f samples\\reactor-sample\\pom.xml -DskipTests verify`
- `cmd /c mvn -q -f samples\\adoption-suite\\pom.xml -DskipTests verify`
