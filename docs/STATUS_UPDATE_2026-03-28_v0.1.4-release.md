# Stage Update - 2026-03-28 v0.1.4 Release Closure

## Overview

This update summarizes the final release-preparation, publication, and post-release
verification work for `spring-correctness-linter` `v0.1.4`.

The most important part of this stage was not adding more rule families. It was
closing the loop between version metadata, release automation, local validation,
and published artifacts so the repository state, tags, and Maven Central outputs
all describe the same release consistently.

## What Happened

### Release Candidate Preparation

- Added `RELEASE_NOTES_v0.1.4.md` as the curated release-note source for the release workflow.
- Updated the root project version to `0.1.4`, moved `scm.tag` to `v0.1.4`, and aligned user-facing copy-paste examples in README, quick-start guides, CI examples, and sample projects.
- Promoted the `CHANGELOG.md` `Unreleased` section into `0.1.4`.

### Failure and Root Cause

The first `v0.1.4` tag push did not publish correctly:

- GitHub Release was missing
- the `Release` workflow failed
- the matching `CI` workflow on the tagged commit failed
- Maven Central still returned `404`

The root cause was a version-alignment gap:

- the root `pom.xml` had already been moved to `0.1.4`
- but `linter-core/pom.xml` and `linter-maven-plugin/pom.xml` still referenced parent version `0.1.3`

That mismatch allowed the repository to look like `0.1.4` at the root while still building child artifacts as `0.1.3` in GitHub Actions, which broke sample/plugin resolution and Central publication.

### Recovery

- Fixed both module parent versions so they now inherit `0.1.4`.
- Added `ReleaseMetadataConsistencyTest` to lock the relationship between:
  - root release version
  - root `scm.tag`
  - `linter-core` parent version
  - `linter-maven-plugin` parent version
- Re-ran local validation, local install, and sample verification after the fix.
- Re-pointed the `v0.1.4` tag to the corrected release-preparation commit and pushed it again.

### Documentation Follow-up

- Added release-failure triage guidance to `docs/RELEASE_PROCESS.md`.
- Added matching maintainer guidance to `docs/MAINTAINER_GUIDE.md`.
- Updated the main English and Chinese README status sections so they now reflect that `v0.1.4` is live.

## Verification

The corrected release path was validated with:

- `./mvnw.cmd -q verify`
- `./mvnw.cmd -q -DskipTests install`
- `./mvnw.cmd -q -Prelease-artifacts clean verify`
- `./mvnw.cmd -q -Pcentral-publish -DskipTests verify`
- `cmd /c mvn -U -q -f samples\\vulnerable-sample\\pom.xml -DskipTests verify`
- `cmd /c mvn -U -q -f samples\\reactor-sample\\pom.xml -DskipTests verify`
- `cmd /c mvn -U -q -f samples\\adoption-suite\\pom.xml -DskipTests verify`
- `powershell -ExecutionPolicy Bypass -File scripts\\check-release-status.ps1 -Version 0.1.4`

## Current Status

- `v0.1.4` now has a GitHub Release.
- The latest `Release` workflow for `v0.1.4` completed successfully.
- The matching `CI` workflow on the corrected release commit also completed successfully.
- Direct Maven Central artifact URLs for both `spring-correctness-linter-core` and `spring-correctness-linter-maven-plugin` now return `200 OK`.
- The repository now includes a guardrail test and maintainer-facing failure-triage notes so the same version-drift failure mode is less likely to recur.

## Remaining Caution

- Existing historical failed workflow runs for the first `v0.1.4` tag push will remain in GitHub Actions history; they should be treated as superseded by the later successful rerun on the corrected tag target.
- Windows release-preparation runs remain more stable with `-Prelease-artifacts clean verify` than with a non-clean rerun after Javadoc output has already been generated.
