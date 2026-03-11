# Accuracy Backlog

This document tracks the highest-value rule-accuracy follow-ups for `spring-correctness-linter`.

The current semantic stack is in good shape: most built-in rules already consume `MethodSemanticFacts`,
`TypeSemanticFacts`, or `SpringSemanticFacts` instead of duplicating raw annotation logic. The remaining
work is less about adding many new rules and more about tightening false-positive and false-negative
boundaries for a few high-impact checks.

## Priority 1

### `SPRING_ENDPOINT_SECURITY`

Current strengths:
- Recognizes common method-security annotations and composed/meta-annotations
- Reuses controller and request-mapping semantics through shared semantic facts

Remaining accuracy gaps:
- Does not model centralized `SecurityFilterChain`, gateway policy, or path-based authorization rules
- Should keep validating class-level and method-level composed security intent as the semantic layer evolves
- May still under-report projects that rely on custom security annotations not meta-annotated with known Spring Security annotations

Recommended next tests:
- [done] Method-level composed security annotations, including `@AliasFor` forwarding and `@PreFilter` usage
- [pending] Suppression guidance for teams with centralized security policy

### `SPRING_CACHEABLE_KEY`

Current strengths:
- Recognizes explicit `key` and `keyGenerator` strategies, including composed annotations with `@AliasFor`
- Excludes zero-argument `@Cacheable` methods because Spring's default key is already stable there
- Honors class-level `@CacheConfig(keyGenerator = ...)` as an explicit cache key strategy
- Recognizes composed annotations that forward `keyGenerator` via `@AliasFor`

Remaining accuracy gaps:
- Still warns on parameterized methods that intentionally rely on Spring's default key generation
- Does not distinguish teams that standardize on cache conventions outside the method declaration site
- Could benefit from better coverage for overloaded signatures and additional composed cache annotations

Recommended next tests:
- [done] Parameterized methods that intentionally rely on default key generation
- [done] Overloaded `@Cacheable` methods with shared cache names
- [pending] Additional `@AliasFor` forwarding for cache-name conventions

### `SPRING_TX_SELF_INVOCATION`

Current strengths:
- Detects both explicit `this.method()` calls and unqualified same-type calls such as `method()`
- Matches same-type calls by method name and argument count to reduce overload noise
- Supports varargs matching while avoiding exact non-transactional overloads
- Covers class-level `@Transactional` by treating public methods as transactional targets
- Includes multi-level inheritance across source roots when type names can be resolved
- Resolves explicit and wildcard imports for inherited targets, including nested types
- Treats proxy-injection patterns (self-injection or ApplicationContext lookups) as out of scope when no explicit self-call is present
- Ignores method references (`this::method`) for self-invocation detection
- Keeps the rule simple and low-noise for the common proxy-bypass pattern

Remaining accuracy gaps:
- Does not fully resolve ambiguous type names across packages or indirect dispatch patterns
- Cannot currently distinguish AspectJ weaving from proxy-based interception beyond documentation and suppression guidance

Recommended next tests:
- None at the moment; revisit when new proxy patterns appear

## Priority 2

### `SPRING_SCHEDULED_TRIGGER_CONFIGURATION`

This rule still uses direct annotation-member reads for good reasons, but it is a candidate for a reusable
scheduled-trigger helper if more schedule-oriented rules are added.

Recommended next tests:
- [done] Placeholder and property-driven trigger values
- Composed `@Scheduled` annotations with forwarded members
- Interactions between repeated schedules and trigger counting

## Working heuristics

When picking the next accuracy task, prefer this order:
1. Add or refine a focused regression test
2. Lift repeated logic into `MethodSemanticFacts`, `TypeSemanticFacts`, or `SpringSemanticFacts`
3. Change the rule implementation only after the shared semantic contract is clear
4. Update rule metadata and contributor docs if the user-visible boundary changed
