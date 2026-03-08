# Repository Guidelines

## Project Structure & Module Organization
This repository is a Maven multi-module Java project. `linter-core/` contains the AST analysis engine, rule implementations, and report generation code under `src/main/java`. `linter-maven-plugin/` wraps the core library as a Maven plugin. Tests live in `linter-core/src/test/java`. Use `samples/vulnerable-sample/` to validate end-to-end plugin behavior against a small Spring app. Root docs such as `README.md`, `CHANGELOG.md`, and `RELEASE_NOTES_*.md` track usage and release history.

## Build, Test, and Development Commands
- `./mvnw -q verify` or `mvnw.cmd -q verify`: compile all modules and run the test suite.
- `./mvnw -q -DskipTests install`: install local artifacts for downstream or sample verification.
- `./mvnw -q -f samples/vulnerable-sample/pom.xml -DskipTests verify`: verify the sample project against the locally built plugin.
- `./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0:lint -Dspring.correctness.linter.writeBaseline=true`: generate a baseline file.

PowerShell may parse dotted `-Dspring.correctness.linter.*` properties unexpectedly; quote them or run Maven through `cmd /c` when testing CLI overrides.

## Coding Style & Naming Conventions
Use Java 17+ and keep the existing 4-space indentation style. Keep package names under `io.github.koyan9.linter...`. Use `UpperCamelCase` for classes, descriptive `lowerCamelCase` for methods and fields, and suffix rule implementations with `Rule` (for example, `TransactionalSelfInvocationRule`). Favor focused, minimal changes over broad refactors. No formatter or lint tool is currently enforced in the build, so match the surrounding style closely.

## Testing Guidelines
Tests use JUnit 5. Name test classes with the `*Test` suffix and place them beside the affected module, usually under `linter-core/src/test/java`. Add or update regression tests for rule behavior, baseline handling, and report output when behavior changes. Before opening a PR, run `mvnw.cmd -q verify` and re-check `samples/vulnerable-sample/` if plugin integration is affected.

## Commit & Pull Request Guidelines
Recent commits use short, imperative subjects such as `Fix CI sample dependency installation` and `Harden GitHub Actions workflows`. Follow that pattern: lead with a verb, keep the subject concise, and keep each commit scoped to one logical change. PRs should summarize the behavior change, list affected modules, reference related issues, and include command output or sample results when modifying reports, rules, or plugin behavior. Update `README.md`, `CHANGELOG.md`, or release notes when contributor-facing behavior changes.
