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

## Baseline demo

- Generate a baseline: `mvn -q spring-correctness-linter:lint -Dspring.correctness.linter.writeBaseline=true`
- Default baseline file: `spring-correctness-linter-baseline.txt`
- Run `mvn -q verify` again to keep only issues not already recorded in the baseline.
