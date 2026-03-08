# Changelog

All notable changes to `spring-correctness-linter` will be documented in this file.

## [Unreleased]

### Added

- Multi-source-root and Maven reactor module scanning support
- Module summaries in JSON / HTML reports and baseline diff output
- `baseline-diff.html` report generation
- Optional per-module baseline and incremental cache output
- Rule enable / disable selection and per-rule severity overrides
- Reactor sample project for multi-module validation

### Changed

- Rule reference markdown now includes configuration and suppression examples
- Analysis pipeline now caches unchanged files and surfaces cache reuse in reports and logs
- HTML report now includes module dashboards and grouped issue presentation
- Quality gate failures now report the affected module IDs

### Fixed

- Sample naming, report paths, and plugin property documentation are aligned with `spring-correctness-linter`
- Parse problems are surfaced instead of being silently dropped from analysis output

## [0.1.0] - 2026-03-07

### Added

- Initial AST-based Spring correctness analysis engine
- JSON / HTML / SARIF reports
- Inline suppression with reason and scope support
- Baseline and baseline diff support
- Severity-based quality gate
- Auto-generated rule reference markdown
- Maven plugin integration
- Vulnerable sample project

### Changed

- Default rule evaluation moved from regex-oriented detection toward AST-based analysis

### Fixed

- Reduced false positives caused by comments and string literals
