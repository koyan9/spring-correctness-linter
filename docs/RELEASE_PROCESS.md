# Release Process

This document describes the recommended release workflow for `spring-correctness-linter`.

## When to prepare versioned release notes

Add a dedicated `RELEASE_NOTES_vX.Y.Z.md` file when the release includes any of the following:

- new rules or meaningful rule-severity changes
- report format changes, baseline behavior changes, or cache behavior changes
- Maven plugin configuration changes
- sample, CI, or adoption guidance changes that users are likely to notice

Template fallback is acceptable for small internal or prerelease builds when you do not need a curated changelog narrative. In that case, the release workflow falls back to `RELEASE_NOTES_TEMPLATE.md` and auto-fills the release name, tag, and prerelease metadata.

## Recommended pre-release checklist

Run these commands from the repository root before triggering the release workflow:

- `mvnw.cmd -q verify` on Windows, or `./mvnw -q verify` on macOS / Linux
- `mvnw.cmd -q -DskipTests install` on Windows, or `./mvnw -q -DskipTests install` on macOS / Linux
- `mvnw.cmd -q -f samples/vulnerable-sample/pom.xml -DskipTests verify` on Windows, or `./mvnw -q -f samples/vulnerable-sample/pom.xml -DskipTests verify` on macOS / Linux
- `mvnw.cmd -q -f samples/reactor-sample/pom.xml -DskipTests verify` on Windows, or `./mvnw -q -f samples/reactor-sample/pom.xml -DskipTests verify` on macOS / Linux
- `mvnw.cmd -q -f samples/adoption-suite/pom.xml -DskipTests verify` on Windows, or `./mvnw -q -f samples/adoption-suite/pom.xml -DskipTests verify` on macOS / Linux

Also confirm the following before release:

- `CHANGELOG.md` reflects the release contents
- `README.md` and `README.zh-CN.md` are aligned if user-facing behavior changed
- release notes mention any new Maven properties, cache invalidation semantics, or SARIF / CI integration changes

## Release workflow inputs

Trigger `.github/workflows/release.yml` with these inputs:

- `tag`: Git tag to release, for example `v0.1.1`
- `release_name`: Human-readable GitHub release title
- `prerelease`: Set to `true` for preview builds and `false` for stable releases

The workflow currently:

- runs `mvn -B -q verify`
- builds release artifacts with the `release-artifacts` profile
- collects generated JARs from `linter-core` and `linter-maven-plugin`
- uses `RELEASE_NOTES_vX.Y.Z.md` when present, otherwise falls back to `RELEASE_NOTES_TEMPLATE.md`
- creates the GitHub release with the generated notes and collected artifacts

## Suggested release-note content

For non-trivial releases, prefer filling these sections explicitly:

- `Highlights`
- `Included Rule Areas`
- `Tooling`
- `Reports and CI`
- `Upgrade and Adoption Notes`
- `Verification`

Keep the notes focused on externally visible changes rather than internal refactors unless those refactors affect extensibility, performance, or upgrade risk.
