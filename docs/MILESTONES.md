# Milestones

## Milestone 1: Usability Recovery

- Fixed sample build failures and restored consistency between the main build and sample validation.
- Removed legacy naming leftovers and aligned default paths, plugin properties, and sample documentation with `spring-correctness-linter`.
- Added the initial contributor guide and stabilized local verification commands.

## Milestone 2: Core Analysis Enhancements

- Added parse-problem visibility so broken Java source is surfaced in reports instead of being silently skipped.
- Added rule enable/disable selection and per-rule severity overrides.
- Improved inline suppression support and compatibility handling.
- Strengthened baseline diff output and module-aware diagnostics.

## Milestone 3: Performance and Scale Support

- Added AST structure caching to reduce repeated traversal work inside rules.
- Added file-content-based incremental analysis cache.
- Added support for multiple source roots in a single analysis run.
- Added Maven reactor scanning for multi-module repositories.

## Milestone 4: Module-Aware Governance

- Added module summaries to JSON and HTML reports.
- Added module-aware quality gate failure messages.
- Added per-module baseline and incremental cache output.
- Added module-aware baseline diff summaries.

## Milestone 5: Reporting Improvements

- Added richer HTML reporting with module dashboards and grouped issue views.
- Added `baseline-diff.html` for human-friendly change review.
- Expanded generated rule reference output with configuration and suppression examples.
- Improved report coverage for JSON, HTML, SARIF, and baseline diff workflows.

## Milestone 6: Tests and Samples

- Added plugin-level tests covering rule selection, severity overrides, cache reuse, reactor scanning, and per-module output behavior.
- Extended core tests for parse problems, baseline diff behavior, multi-source-root analysis, and cache reuse.
- Added a new multi-module reactor sample alongside the single-module vulnerable sample.
- Updated CI to validate `samples/vulnerable-sample/`, `samples/reactor-sample/`, and `samples/adoption-suite/`.

## Milestone 7: Quality Gates and Coverage

- Added JaCoCo module reports and aggregate coverage reports.
- Uploaded coverage reports as CI artifacts.
- Enforced minimum line coverage thresholds for `linter-core` and `linter-maven-plugin`.

## Milestone 8: Maintainability Refactors

- Split `ReportWriter` into format-specific writer helpers.
- Split `CorrectnessLintMojo` into smaller helper classes for option parsing, source-root resolution, path strategy, baseline writing, execution planning, report emission, and failure message construction.
- Reduced the size and responsibility of major orchestration classes to improve future maintainability.

## Milestone 9: Documentation and Presentation

- Rewrote `README.md` as a self-contained project guide.
- Added `README.zh-CN.md` as a Chinese counterpart.
- Improved `CONTRIBUTING.md`, `CHANGELOG.md`, and release note templates.
- Added clear project highlights for external presentation.

## Milestone 10: Accuracy and Governance Polish

- Added governance-oriented outputs such as `rules-governance.json` and lightweight JSON report mode.
- Tightened cache-related accuracy boundaries around blank key strategies, exact allowlist matching, composed cache metadata, and project-wide key-generator conventions.
- Tightened endpoint-security accuracy around inherited security annotations and centralized-security auto-detection for both servlet and reactive Spring Security styles.
- Hardened self-invocation detection with direct method-reference coverage and aligned async/transaction behavior.
- Added explicit file-analysis concurrency controls and strengthened regression coverage around new plugin and core options.

## Milestone 11: Extensibility and Proxy-Boundary Expansion

- Added lightweight external rule discovery through `ServiceLoader`, along with an adoption-suite sample and plugin-level validation coverage.
- Added repeatable release-status checks plus copy-paste CI and post-release verification guidance for maintainers.
- Expanded low-noise built-in proxy-boundary coverage across async, cache, and transactional event-listener scenarios.
- Added English and Chinese rule coverage matrices so the current built-in rule surface is easier to inspect during rollout planning.

## Current Status

- The project now provides a practical Spring correctness linting workflow suitable for local development, CI enforcement, legacy baseline adoption, multi-module repository governance, and policy-oriented rollout workflows.
- The built-in rule surface is now materially broader, but recent growth stayed concentrated on low-noise, testable Spring proxy-boundary semantics rather than speculative framework heuristics.
- Future work should continue focusing on incremental polish, rule-accuracy discipline, and real-user adoption feedback rather than large new feature batches.
- For a detailed summary of the latest proxy-boundary and extensibility batch, see `docs/STATUS_UPDATE_2026-03-27_proxy-boundary-expansion.md`.
- For the current near-term direction, see `docs/ROADMAP.md`.
