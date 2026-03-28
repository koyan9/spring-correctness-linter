# Stage Update - 2026-03-28 v0.1.6 Release Closure

## Overview

This update summarizes the `v0.1.6` release closure for `spring-correctness-linter`.

`v0.1.6` is a narrow follow-up release intended to supersede the post-release CI gap that remained
on `v0.1.5`. The release does not introduce a new product theme. It closes the loop on the
component-scanned auto-detect work by restoring a green consumer-sample path on clean runners.

## What Changed

- Added the missing `jakarta.servlet-api` dependency to `samples/adoption-suite/centralized-security-app`.
- Kept the `v0.1.5` accuracy and release-hygiene changes intact while moving the maintained release
  line forward to a fully green tag and `main` commit.
- Added `RELEASE_NOTES_v0.1.6.md` and aligned project metadata to `0.1.6` / `v0.1.6`.

## Verification

The release-preparation path was revalidated with:

- `./mvnw.cmd -q verify`
- `./mvnw.cmd -q -Prelease-artifacts clean verify`
- `./mvnw.cmd -q -Pcentral-publish -DskipTests verify`
- `cmd /c mvn -q -f samples\\vulnerable-sample\\pom.xml -DskipTests verify`
- `cmd /c mvn -q -f samples\\reactor-sample\\pom.xml -DskipTests verify`
- `cmd /c mvn -q -f samples\\adoption-suite\\pom.xml -DskipTests verify`

## Current Status

- The GitHub Release for `v0.1.6` is live.
- The matching `Release` workflow for `v0.1.6` completed successfully.
- The matching `CI` workflow for the `v0.1.6` preparation commit also completed successfully.
- `v0.1.6` should be treated as the current maintained release line unless a later release is
  explicitly prepared.
- Maven Central propagation should be verified with
  `powershell -ExecutionPolicy Bypass -File scripts\\check-release-status.ps1 -Version 0.1.6`
  because public visibility can lag behind workflow completion.

## Maintenance Note

- No additional release candidate is currently scoped after `v0.1.6`.
- Future roadmap or rule-growth planning should be revisited only when active development resumes.
