# spring-correctness-linter

`spring-correctness-linter` is a CI-first Spring correctness linter.

## Coordinates

- `io.github.koyan9:spring-correctness-linter-parent`
- `io.github.koyan9:spring-correctness-linter-core`
- `io.github.koyan9:spring-correctness-linter-maven-plugin`

## Capabilities

- AST-based Java source analysis
- JSON / HTML / SARIF reports
- Inline suppression with reason and scope support
- Baseline and baseline diff
- Severity-based quality gates
- Auto-generated rule reference markdown

## Repo Layout

- `linter-core/`
- `linter-maven-plugin/`
- `samples/vulnerable-sample/`
- `CHANGELOG.md`
- `RELEASE_NOTES_TEMPLATE.md`

## Commands

- Verify: `./mvnw -q verify`
- Generate baseline: `./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0-SNAPSHOT:lint -DwriteBaseline=true`
- Fail on severity: `./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0-SNAPSHOT:lint -DfailOnSeverity=WARNING`
