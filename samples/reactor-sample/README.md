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

## Module Output

- Per-module baseline files: `samples/reactor-sample/modules/<module>/spring-correctness-linter-baseline.txt`
- Per-module caches: `samples/reactor-sample/target/spring-correctness-linter/modules/<module>/analysis-cache.txt`

The parent reactor report groups findings by module, and child modules are skipped automatically when `scanReactorModules=true`.
