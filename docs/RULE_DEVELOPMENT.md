# Rule Development Guide

This guide describes the preferred workflow for adding or refactoring rules in `spring-correctness-linter`.

## Preferred semantic layers

When implementing a rule, prefer the highest available abstraction layer.

1. `MethodSemanticFacts` / `TypeSemanticFacts`
2. `SpringSemanticFacts`
3. `JavaSourceInspector`
4. Raw JavaParser traversal in the rule itself

In practice, this means:

- first look for an existing high-level boolean or fact on `MethodSemanticFacts` or `TypeSemanticFacts`
- if none exists, add a reusable helper to `SpringSemanticFacts`
- only extend `JavaSourceInspector` or `AnnotationMetadataIndex` when the missing capability is a lower-level semantic primitive that multiple rules may need
- avoid embedding one-off annotation-resolution logic directly inside a rule when the same concept could be reused elsewhere

## Current semantic stack

- `JavaSourceInspector`: low-level AST and annotation inspection
- `AnnotationMetadataIndex`: composed annotations, meta-annotations, and `@AliasFor` support
- `AnnotationMetadataIndex` only expands annotations defined in the current source tree; external dependency annotations are not resolved.
- `SpringSemanticFacts`: shared Spring-aware semantic queries for a `SourceUnit`
- `TypeSemanticFacts`: structured type-level semantics
- `MethodSemanticFacts`: structured method-level semantics

Rules should normally obtain semantic access through:

```java
SpringSemanticFacts facts = context.springFacts(sourceUnit);
```

and then consume `facts.typeFacts(...)` or `facts.methodFacts(...)` where possible.

## When to add new helpers

Add or evolve semantic helpers when:

- two or more rules need the same composed annotation logic
- a rule would otherwise duplicate controller / scheduling / lifecycle / transaction boundary checks
- a new rule domain needs a reusable concept such as "startup callback" or "explicit security intent"

Examples of good helper additions:

- `methodFacts.isPublicRequestMapping()`
- `methodFacts.isScheduledAsyncBoundary()`
- `typeFacts.hasExplicitSecurityIntent()`
- `methodFacts.hasHighRiskTransactionPropagation()`
- `methodFacts.hasExplicitCacheKeyStrategy()`

Examples of helper additions that likely belong one layer lower:

- resolving composed annotation names
- reading aliased annotation member values
- identifying framework callback signatures such as `afterPropertiesSet()`

## Type-resolution guidance

Some rules (for example `SPRING_TX_SELF_INVOCATION`) need to resolve type names across source roots without a full symbol solver. The current approach is intentionally conservative:

- prefer same-package matches
- honor explicit imports and wildcard imports when resolving inherited types
- only fall back to unique simple-name matches when no ambiguity exists
- skip ambiguous matches to avoid false positives

Use `ProjectContext.typeResolutionIndex()` and `TypeResolutionIndex` for this shared resolution behavior, rather than duplicating ad-hoc lookup logic in each rule. When extending these rules, keep the resolution strategy predictable and document any new heuristics or edge cases in `docs/ACCURACY_BACKLOG.md`.

## Preferred rule style

As the semantic layer grows, built-in rules should follow this pattern whenever possible:

1. obtain facts once through `context.springFacts(sourceUnit)`
2. obtain `typeFacts` or `methodFacts` for the current node
3. prefer high-level booleans such as `isPublicRequestMapping()` or `hasTransactionalBoundary()`
4. fall back to mid-level member-value helpers only for configuration-heavy cases such as scheduling cadence or transaction propagation

This keeps rule implementations small and makes it easier to improve correctness in one place.

Examples where mid-level member reads are still acceptable today:

- `@Scheduled` cadence configuration like `fixedRate`, `fixedDelay`, `cron`, or `initialDelay`
- transaction propagation members such as `REQUIRES_NEW` and `NESTED` when no higher-level helper exists yet
- composed annotation attribute forwarding that still needs direct value access through `@AliasFor`

When one of these patterns appears in more than one or two rules, prefer promoting it into a higher-level helper on `MethodSemanticFacts`, `TypeSemanticFacts`, or `SpringSemanticFacts`.

## Adding a new rule

For a new built-in rule, complete the following steps:

1. Implement the rule in `linter-core/src/main/java/io/github/koyan9/linter/core/rules/`
2. Assign a stable rule id and `RuleDomain`
3. Fill rule metadata:
   - `title()`
   - `description()`
   - `appliesWhen()`
   - `commonFalsePositiveBoundaries()`
   - `recommendedFixes()`
4. Register the rule in `BuiltInRuleRegistry`
5. Update registry tests for rule order and rule groups
6. Add regression coverage in `ProjectLinterTest` or adjacent tests
7. Update sample code if the rule is worth demonstrating end-to-end
8. Update contributor-facing docs when the rule changes adoption guidance or recommended bundles
9. Update `CHANGELOG.md`

## External rule providers

If you want to ship rules outside the built-in registry, prefer the lightweight provider path:

1. Implement `io.github.koyan9.linter.core.spi.LintRuleProvider`
2. Return one or more `LintRule` instances from `rules()`
3. Add `META-INF/services/io.github.koyan9.linter.core.spi.LintRuleProvider`
4. Publish that JAR and add it as a dependency of the Maven plugin declaration

Provider guidance:

- keep rule ids globally unique, including against built-in rules
- prefer stable provider jars with explicit versions
- treat provider loading failures as startup-time misconfiguration, not as soft warnings

## Testing guidance

Use the narrowest useful test first, then broader verification:

- rule-level regression in `ProjectLinterTest`
- registry / selection test updates when registration changes
- report-writer tests if the new rule changes rule metadata or report content
- sample validation when behavior is user-visible and worth demonstrating

Good regression tests should prefer:

- minimal Java snippets with just enough Spring surface to trigger the rule
- explicit assertions on rule ids rather than broad string matching where possible
- separate tests for composed annotations or `@AliasFor` behavior when that logic matters

## Recommended contributor behavior

- keep rule logic small and declarative
- prefer shared semantic helpers over repeated string-based annotation checks
- keep diagnostics specific about the risky Spring runtime behavior
- treat report and sample updates as part of the rule change when they improve adoption or explainability

## When to touch samples

Update `samples/vulnerable-sample/` when a new rule is best demonstrated in a single-module setting.

Update `samples/reactor-sample/` when the rule or behavior is specifically about:

- multi-module grouping
- reactor scanning
- per-module outputs
- cache or reporting behavior across modules

If a rule is not yet suitable for end-to-end demonstration, a focused regression test is enough.
