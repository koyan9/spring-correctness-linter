# Stage Update - 2026-03-26

## Overview

This update summarizes the recent hardening work completed on `spring-correctness-linter`.

The focus of this batch was not on adding new rule families. Instead, it concentrated on:

- tightening reactor and source-root behavior
- expanding regression coverage for public Maven configuration contracts
- expanding regression coverage for high-value cache and endpoint-security boundaries
- aligning CI, samples, changelog, and bilingual docs with current behavior

## What Changed

### Reactor and Source Roots

- Aggregator-only or Java-empty source roots are now excluded from module reports and per-module baseline/cache outputs.
- Reactor scans continue to support module grouping and per-module artifacts, but only for modules that actually contribute Java sources.

### Plugin Configuration Contracts

Regression coverage was expanded for:

- `failOnSeverity` and `failOnError` precedence
- invalid `failOnSeverity` values
- mixed valid and invalid `formats`
- blank `ruleDocsFileName` fallback
- `writeRuleDocs` / governance output behavior
- `includeTestSourceRoots`
- `moduleSourceDirectories` validation and syntax errors
- unknown or conflicting enabled/disabled rule and rule-domain settings

### Rule Accuracy

Regression coverage was expanded for:

- inherited custom and composed endpoint-security intent
- blank cache keys
- zero-argument cacheables
- composed type-level `@CacheConfig(keyGenerator=...)`
- composed single-member cache allowlists

### CI, Samples, and Docs

- CI now validates `samples/adoption-suite/` in addition to the vulnerable and reactor samples.
- Sample READMEs and the main bilingual docs now describe the current governance outputs, `securityAnnotations` normalization, `GENERAL` semantics, and reactor module filtering more precisely.
- `CHANGELOG.md` and `RELEASE_NOTES_v0.1.2.md` now reflect the current change set.

## Verification

The following checks were run during this iteration:

- `./mvnw.cmd -q -pl linter-core -Dtest=ProjectLinterTest test`
- `./mvnw.cmd -q -pl linter-maven-plugin -Dtest=CorrectnessLintMojoTest test`
- `./mvnw.cmd -q verify`
- `cmd /c mvn -q -f samples\\reactor-sample\\pom.xml clean verify -DskipTests`
- `cmd /c mvn -q -f samples\\adoption-suite\\pom.xml -DskipTests verify`

## Current Status

- The main branch has been updated and pushed.
- The latest CI run on `main` is green for both Java 17 and Java 21.
- Release notes for `v0.1.2` have been drafted, but no tag has been created yet.

## Remaining Caution

- `securityAnnotations` intentionally remains simple-name based, so same-named annotations in different packages are still ambiguous by design.
- The largest runtime behavior change in this batch is the narrower reactor/module output for nonexistent or Java-empty roots.
