# adoption-suite

This suite models how external Spring projects would consume `spring-correctness-linter` rather than how the linter itself is developed.

Each app is intentionally small and focused:

- `basic-app`: plain single-module Spring app with a minimal plugin setup
- `centralized-security-app`: demonstrates `autoDetectCentralizedSecurity`
- `cache-convention-app`: demonstrates `autoDetectProjectWideKeyGenerator`

## Usage

1. Install the current plugin from the repository root:
   - Windows: `mvnw.cmd -q -DskipTests install`
   - macOS / Linux: `./mvnw -q -DskipTests install`
2. Run one app directly:
   - `mvn -q -f samples/adoption-suite/basic-app/pom.xml -DskipTests verify`
   - `mvn -q -f samples/adoption-suite/centralized-security-app/pom.xml -DskipTests verify`
   - `mvn -q -f samples/adoption-suite/cache-convention-app/pom.xml -DskipTests verify`
3. Or build the whole suite:
   - `mvn -q -f samples/adoption-suite/pom.xml -DskipTests verify`

## What Each App Demonstrates

### `basic-app`

- a conventional plugin declaration in a consumer project
- normal JSON / HTML outputs
- visible findings with no baseline or special conventions enabled
- expected result: non-zero issues

### `centralized-security-app`

- public controller endpoints
- a centralized `SecurityFilterChain` bean
- `autoDetectCentralizedSecurity=true` suppressing controller-security noise
- expected result: `0` visible issues

### `cache-convention-app`

- parameterized `@Cacheable` methods without explicit key declarations
- a project-wide key convention through `CachingConfigurerSupport`
- `autoDetectProjectWideKeyGenerator=true` suppressing cache-key noise
- expected result: `0` visible issues

## Expected Findings

- `basic-app`
  - expected to report visible findings
  - currently demonstrates `SPRING_ASYNC_VOID` and `SPRING_ENDPOINT_SECURITY`
- `centralized-security-app`
  - expected to report `0` visible issues because centralized-security auto-detection is enabled
- `cache-convention-app`
  - expected to report `0` visible issues because project-wide key-generator auto-detection is enabled

## Suggested Validation Commands

- Governance output:
  - `mvn -q -f samples/adoption-suite/basic-app/pom.xml -DskipTests verify`
  - inspect `target/spring-correctness-linter/rules-governance.json`
- Lightweight JSON:
  - `mvn -q -f samples/adoption-suite/basic-app/pom.xml -DskipTests verify "-Dspring.correctness.linter.formats=json" "-Dspring.correctness.linter.lightweightReports=true"`
- Serial analysis:
  - `mvn -q -f samples/adoption-suite/basic-app/pom.xml -DskipTests verify "-Dspring.correctness.linter.parallelFileAnalysis=false"`

## Notes

- This suite is not part of the main repository build modules on purpose.
- It exists to validate real-project adoption paths without coupling consumer scenarios to the core product build.
- It is still verified explicitly in CI so these consumer-style adoption paths remain covered.
