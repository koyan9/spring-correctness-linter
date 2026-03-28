# Accuracy Backlog

This document tracks the highest-value rule-accuracy follow-ups for `spring-correctness-linter`.

The current semantic stack is in good shape: most built-in rules already consume `MethodSemanticFacts`,
`TypeSemanticFacts`, or `SpringSemanticFacts` instead of duplicating raw annotation logic. The remaining
work is less about adding many new rules and more about tightening false-positive and false-negative
boundaries for a few high-impact checks.

Recent low-noise expansions already covered the most obvious proxy-boundary gaps in `ASYNC` and `CACHE`,
and added an initial async-over-transaction-phase listener review in `EVENTS`. That means the backlog
should stay biased toward accuracy refinement instead of continuing unchecked rule-count growth.

When selecting `v0.1.5` work, prefer the narrow scope described in `docs/V0.1.5_CANDIDATE_SCOPE.md`.

## Priority 1

### `SPRING_ENDPOINT_SECURITY`

Current strengths:
- Recognizes common method-security annotations and composed/meta-annotations
- Reuses controller and request-mapping semantics through shared semantic facts
- Honors inherited security intent from interfaces and base classes
- Supports opt-in centralized-security auto-detection for `SecurityFilterChain` and `SecurityWebFilterChain`

Remaining accuracy gaps:
- Does not model centralized `SecurityFilterChain`, gateway policy, or path-based authorization rules
- Should keep validating class-level and method-level composed security intent as the semantic layer evolves
- May still under-report projects that rely on custom security annotations not meta-annotated with known Spring Security annotations

Recommended next tests:
- [done] Method-level composed security annotations, including `@AliasFor` forwarding and `@PreFilter` usage
- [done] Suppression guidance for teams with centralized security policy
- [done] Auto-detection for servlet and reactive centralized-security beans
- Candidate revisit only if a concrete real-world false negative appears around inherited or centralized policy hints

### `SPRING_CACHEABLE_KEY`

Current strengths:
- Recognizes explicit `key` and `keyGenerator` strategies, including composed annotations with `@AliasFor`
- Excludes zero-argument `@Cacheable` methods because Spring's default key is already stable there
- Honors class-level `@CacheConfig(keyGenerator = ...)` as an explicit cache key strategy
- Recognizes composed annotations that forward `keyGenerator` via `@AliasFor`
- Treats blank `key` / `keyGenerator` values as missing
- Supports exact allowlist matching for direct and composed cache names, including `@CacheConfig`
- Supports opt-in auto-detection of project-wide `@Bean KeyGenerator` usage

Remaining accuracy gaps:
- Still warns on parameterized methods that intentionally rely on Spring's default key generation
- Does not distinguish teams that standardize on cache conventions outside the method declaration site
- Could benefit from broader project-level key-convention modeling beyond direct `KeyGenerator` bean detection

Recommended next tests:
- [done] Parameterized methods that intentionally rely on default key generation
- [done] Overloaded `@Cacheable` methods with shared cache names
- [done] Additional `@AliasFor` forwarding for cache-name conventions
- [done] Composed `@CacheConfig` and single-member `@Cacheable` allowlist matching
- [done] Exact allowlist matching without substring false negatives
- [done] Project-wide `KeyGenerator` bean detection
- Candidate revisit only if a concrete low-noise signal appears beyond direct `KeyGenerator`, `CachingConfigurer`, or explicit allowlists

### `SPRING_TX_SELF_INVOCATION`

Current strengths:
- Detects both explicit `this.method()` calls and unqualified same-type calls such as `method()`
- Matches same-type calls by method name and argument count to reduce overload noise
- Supports varargs matching while avoiding exact non-transactional overloads
- Covers class-level `@Transactional` by treating public methods as transactional targets
- Includes multi-level inheritance across source roots when type names can be resolved
- Resolves explicit and wildcard imports for inherited targets, including nested types
- Treats proxy-injection patterns (self-injection or ApplicationContext lookups) as out of scope when no explicit self-call is present
- Flags direct self method references such as `this::method`
- Keeps the rule simple and low-noise for the common proxy-bypass pattern

Remaining accuracy gaps:
- Does not fully resolve ambiguous type names across packages or indirect dispatch patterns
- Cannot currently distinguish AspectJ weaving from proxy-based interception beyond documentation and suppression guidance

Recommended next tests:
- None at the moment; revisit when new proxy patterns appear

### Async and Cache Proxy-Boundary Families

Current strengths:
- `ASYNC` now covers private methods, final methods, final classes without interfaces, self-invocation, and unsupported return types
- `CACHE` now covers explicit key strategy review, private methods, final methods, final classes without interfaces, self-invocation, and multi-annotation combinations
- The vulnerable sample and generated rule docs now demonstrate these proxy-boundary families more explicitly

Remaining accuracy gaps:
- None that currently justify immediate follow-up without concrete user reports
- Future work here should prefer bug-fix-style refinements rather than adding more pattern rules by default

Recommended next tests:
- None at the moment; revisit only if a concrete false positive or false negative is reported

## Priority 2

### `SPRING_SCHEDULED_TRIGGER_CONFIGURATION`

This rule still uses direct annotation-member reads for good reasons, but it is a candidate for a reusable
scheduled-trigger helper if more schedule-oriented rules are added.

Recommended next tests:
- [done] Placeholder and property-driven trigger values
- [done] Composed `@Scheduled` annotations with forwarded members
- [done] Interactions between repeated schedules and trigger counting

## Priority 3

### `SPRING_TRANSACTIONAL_EVENT_LISTENER_ASYNC`

Current strengths:
- Detects direct `@TransactionalEventListener` + `@Async` combinations
- Also detects class-level `@Async` on public transactional event-listener methods
- Keeps the rule advisory and intentionally narrow around transaction-phase plus cross-thread delivery semantics

Remaining accuracy gaps:
- Does not model executor configuration or delivery ordering beyond the explicit async boundary
- Package-private methods in a class-level `@Async` type are intentionally not treated as inherited async entrypoints

Recommended next tests:
- [done] Class-level `@Async` with public transactional event listeners
- Candidate revisit if real projects rely heavily on meta-annotated event-listener wrappers or more complex listener inheritance

## Working heuristics

When picking the next accuracy task, prefer this order:
1. Add or refine a focused regression test
2. Lift repeated logic into `MethodSemanticFacts`, `TypeSemanticFacts`, or `SpringSemanticFacts`
3. Change the rule implementation only after the shared semantic contract is clear
4. Update rule metadata and contributor docs if the user-visible boundary changed
