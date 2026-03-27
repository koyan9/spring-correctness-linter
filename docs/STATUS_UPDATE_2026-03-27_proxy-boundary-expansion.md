# Stage Update - 2026-03-27 Proxy-Boundary Expansion

## Overview

This update summarizes the focused follow-up work completed after the broader
cache, observability, and release-hygiene improvements on `spring-correctness-linter`.

The goal of this batch was pragmatic:

- keep rule growth narrow and low-noise instead of introducing broad speculative rule families
- fill obvious Spring proxy-boundary gaps where runtime behavior is well understood
- keep samples, release hygiene, and README guidance aligned with the newly expanded built-in rule set

## What Changed

### External Rule Extensibility

- Added lightweight external rule discovery through `ServiceLoader` via `io.github.koyan9.linter.core.spi.LintRuleProvider`.
- Added `samples/adoption-suite/external-rules-app/` to validate end-to-end external rule loading through ordinary Maven dependencies.
- Added plugin regression coverage so externally supplied rules are visible to the Maven integration path, not just to `linter-core`.

### Release and Adoption Hygiene

- Added `scripts/check-release-status.ps1` so maintainers can verify GitHub Release, workflow completion, and Maven Central visibility with one repeatable command.
- Added copy-paste CI examples and post-release verification guidance to the maintainer-facing docs.
- Kept changelog, sample docs, and release-oriented guidance aligned with the `v0.1.3` publication state.

### Built-in Rule Expansion

The built-in rule set was expanded with a narrow proxy-boundary focus:

- `SPRING_ASYNC_UNSUPPORTED_RETURN_TYPE`
- `SPRING_ASYNC_FINAL_CLASS`
- `SPRING_CACHEABLE_SELF_INVOCATION`
- `SPRING_CACHEABLE_PRIVATE_METHOD`
- `SPRING_CACHEABLE_FINAL_METHOD`
- `SPRING_CACHEABLE_FINAL_CLASS`
- `SPRING_TRANSACTIONAL_EVENT_LISTENER_ASYNC`

These additions intentionally favored high-confidence Spring runtime semantics:

- async and cache proxy boundaries now cover `private`, `final method`, `final class`, and self-invocation patterns
- event-timing coverage now also flags `@TransactionalEventListener` methods that cross an async boundary
- registry order, per-domain grouping, vulnerable samples, and regression tests were updated alongside each rule so documentation and runtime behavior stayed aligned

### Documentation Alignment

- Added a rule coverage matrix to `README.md`.
- Synced the same rule coverage matrix and current rule scope summary into `README.zh-CN.md`.
- Refreshed sample documentation so `samples/vulnerable-sample/` demonstrates the new proxy-boundary findings directly.

## Verification

The rule-expansion batch was repeatedly validated with:

- `./mvnw.cmd -q -pl linter-core -am test`
- `cmd /c mvn -q -f samples\\vulnerable-sample\\pom.xml -DskipTests verify`
- `./mvnw.cmd -q verify`

## Current Status

- The built-in rule set now exposes 35 default rules across `ASYNC`, `LIFECYCLE`, `SCHEDULED`, `CACHE`, `WEB`, `TRANSACTION`, `EVENTS`, and `CONFIGURATION`.
- The proxy-boundary families are now most complete in `ASYNC`, `CACHE`, and `TRANSACTION`, where the common Spring AOP failure modes are covered consistently.
- `EVENTS` coverage remains intentionally smaller, but it now includes the highest-value low-noise async + transaction-phase listener overlap.
- Repository documentation now better explains both governance-oriented rollout and the actual current rule surface in English and Chinese.

## Remaining Caution

- The project still prefers explicit, testable Spring semantics over convention-heavy guesses; broad framework-specific heuristics are still intentionally avoided.
- `EVENTS`, `SCHEDULED`, and `LIFECYCLE` remain mostly advisory domains, so future growth there should stay conservative and evidence-driven.
- External rule discovery is intentionally lightweight; future work should preserve that simplicity unless real adopters demonstrate a stronger need.
