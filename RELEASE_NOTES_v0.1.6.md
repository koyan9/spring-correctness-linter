# spring-correctness-linter v0.1.6

## Highlights

- Fixes the adoption-suite `centralized-security-app` sample so the component-scanned `SecurityFilterChain` example compiles cleanly on GitHub Actions and fresh local environments
- Preserves the `v0.1.5` auto-detect and release-guardrail work while restoring a green post-release CI state on the sample suite

## Included Rule Areas

- Added:
  - None
- Expanded:
  - None
- Removed or deprecated:
  - None

## Tooling

- Maven plugin changes:
  - None
- Report generation changes:
  - None
- Sample project changes:
  - `samples/adoption-suite/centralized-security-app` now declares the explicit `jakarta.servlet-api` dependency required by the sample `SecurityFilterChain` implementation

## Breaking Changes

- None

## Reports and CI

- JSON / HTML report changes:
  - No schema change
- SARIF or code scanning changes:
  - No SARIF schema change
- Baseline / baseline diff changes:
  - No baseline format change
- Multi-module, reactor, or cache behavior changes:
  - None
- Performance or cache evidence:
  - Not applicable for this post-release CI fix

## Upgrade and Adoption Notes

- Recommended Maven configuration changes:
  - None
- New or renamed Maven properties:
  - None
- Cache invalidation or analysis fingerprint notes:
  - None
- PowerShell, sample, or CI workflow notes:
  - This release is intended to supersede the `v0.1.5` sample-compilation issue on clean GitHub Actions runners

## Verification

- `./mvnw.cmd -q verify`
- `./mvnw.cmd -q -Prelease-artifacts clean verify`
- `./mvnw.cmd -q -Pcentral-publish -DskipTests verify`
- `cmd /c mvn -q -f samples\\adoption-suite\\pom.xml -DskipTests verify`
