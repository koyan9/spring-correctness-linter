# Stage Update - 2026-03-27 Accuracy Follow-up

## Overview

This follow-up update summarizes the rule-accuracy work completed after the broader
cache, observability, and benchmarking improvements on `spring-correctness-linter`.

The focus of this batch was narrow on purpose:

- tighten inherited endpoint-security matching to reduce low-noise false negatives
- add Spring-official `@Caching(cacheable = ...)` coverage to cache-key accuracy
- keep the implementation inside the shared semantic layer instead of adding one-off rule logic

## What Changed

### Endpoint Security Accuracy

- `SPRING_ENDPOINT_SECURITY` no longer treats inherited secured overloads with the same method name and arity but different parameter types as implicitly protected.
- Inherited method-security matching now uses normalized parameter signatures instead of a looser name-plus-arity heuristic.
- Class-level inherited security behavior is unchanged.

### Cache Key Accuracy

- `SPRING_CACHEABLE_KEY` now recognizes Spring's `@Caching(cacheable = @Cacheable(...))` container form.
- Explicit `key` / `keyGenerator` handling for container-declared cacheables now behaves the same way as direct `@Cacheable`.
- Default-key allowlists now also apply to `@Caching(cacheable = @Cacheable(...))`.

### Semantic-Layer Changes

- Added reusable nested-annotation helpers in `JavaSourceInspector`.
- Added reusable cacheable-operation extraction in `SpringSemanticFacts`.
- `MethodSemanticFacts` now distinguishes whether a method has any effective cacheable operation independently from whether that operation already has an explicit key strategy.

## Verification

The following checks were run during this iteration:

- `cmd /c .\\mvnw.cmd -q -pl linter-core "-Dtest=SpringSemanticFactsTest,ProjectLinterTest" test`
- `./mvnw.cmd -q verify`

## Current Status

- The highest-value low-noise follow-ups for `SPRING_ENDPOINT_SECURITY` and `SPRING_CACHEABLE_KEY` have been tightened again without broadening speculative project-convention modeling.
- The semantic layer remains the primary place where cache and security matching behavior is centralized.
- The repository now also supports lightweight external rule providers through `ServiceLoader`, and the adoption suite includes an end-to-end validation sample for that integration path.

## Remaining Caution

- `SPRING_ENDPOINT_SECURITY` still does not model centralized path-based authorization policy beyond explicit annotations and the existing centralized-security switches.
- `SPRING_CACHEABLE_KEY` still intentionally avoids broad guesses about project-wide cache conventions outside explicit signals such as `KeyGenerator`, `CachingConfigurer`, and configured allowlists.
