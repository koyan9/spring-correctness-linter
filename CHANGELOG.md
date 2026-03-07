# Changelog

All notable changes to `spring-correctness-linter` will be documented in this file.

## [Unreleased]

### Added

- 

### Changed

- 

### Fixed

- 

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