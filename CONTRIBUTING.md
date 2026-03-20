# Contributing

## Local Setup

- Use Java `17+`
- Preferred commands:
  - macOS / Linux: `./mvnw -q verify`
  - Windows: `mvnw.cmd -q verify`

## Workflow

- Keep changes focused and minimal
- Add or update tests for behavior changes
- Update README / CHANGELOG / release notes template when needed
- Treat `main` as protected: open a PR for normal changes instead of pushing directly
- Validate project-local samples before release if your change affects integration behavior
- Validate both `samples/vulnerable-sample/` and `samples/reactor-sample/` when changing plugin integration, module scanning, baseline handling, or report generation
- When preparing a release, add `RELEASE_NOTES_vX.Y.Z.md`; pushing the annotated `vX.Y.Z` tag then triggers automatic publication
- Follow `docs/RELEASE_PROCESS.md` for the recommended release checklist, protected-branch expectations, and release-note guidance
- Follow `docs/RULE_DEVELOPMENT.md` when adding or refactoring rules so semantic helpers and rule structure stay consistent
- Check `docs/ACCURACY_BACKLOG.md` when choosing the next rule-hardening or false-positive-reduction task

## Validation Checklist

- Always run `mvnw.cmd -q verify` or `./mvnw -q verify`
- Run `samples/vulnerable-sample/` when changing report formats, suppressions, baseline behavior, or single-module scanning
- Run `samples/reactor-sample/` when changing module grouping, reactor scanning, multi-source-root behavior, or per-module baseline/cache output
- Run `mvnw.cmd -q -DskipTests install` before release-related changes or when local sample installation behavior changes
- Review JaCoCo output in module `target/site/jacoco/` directories and `target/site/jacoco-aggregate/` when changing analysis or reporting logic
- Keep `linter-core` JaCoCo line coverage at or above `85%` when adding or refactoring analysis logic
- Keep `linter-maven-plugin` JaCoCo line coverage at or above `75%` when changing plugin wiring or Maven integration behavior
