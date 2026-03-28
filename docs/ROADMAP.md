# Roadmap

This roadmap describes the current near-term direction for `spring-correctness-linter`.

The project already covers the core linting workflow: AST-based analysis, Maven integration, baseline adoption, incremental cache reuse, multi-module reactor scanning, and JSON / HTML / SARIF reporting. The next phase focuses on incremental polish, observability, and adoption quality rather than large feature batches.

## Near-term priorities

### 1. Performance observability and trust

- Expand runtime metrics only where they improve decision-making for users of large repositories
- Keep cache reuse transparent with clear analyzed-vs-cached counts and stable invalidation semantics
- Improve report readability for timing-heavy or multi-module scans without overloading the default output

### 2. Adoption and release usability

- Keep README, release notes, and sample projects aligned with the current feature set
- Strengthen release hygiene so tagged builds, release notes, and workflow behavior stay predictable
- Keep generated rule-reference output aligned with the current built-in rule surface and rollout guidance

Current recommended adoption strategy:

- Start with a domain bundle before enabling the full default rule set
- Use `CI Starter` (`ASYNC,TRANSACTION,WEB`) as the default first pass for new projects
- Use `Transaction Focus` (`TRANSACTION,EVENTS`) when teams want to harden transaction and event timing semantics first
- Use `Web/API Focus` (`WEB`) for controller exposure and endpoint security intent reviews
- Use `Cache Focus` (`CACHE`) when validating cache behavior in isolation before broader rollout
- Use generated `rules-reference.md` to expand bundles into concrete rule IDs after the first rollout pass
- Validate `CI Starter`, transaction, web, and configuration-oriented adoption paths with `samples/vulnerable-sample/`
- Validate reactor/module grouping and async-heavy bundle behavior with `samples/reactor-sample/`
- Validate consumer-style rollout paths such as centralized security and project-wide cache conventions with `samples/adoption-suite/`

### 3. Maintainable rule growth

- Keep rule growth narrow, low-noise, and easy to explain to adopters
- Prefer improvements to rule metadata, generated docs, test coverage, and report clarity before adding broad speculative rule families
- Preserve the current lightweight external-rule path without turning it into a heavy plugin framework too early

## Suggested next 1-2 iterations

Track rule-accuracy follow-ups and regression-test priorities in `docs/ACCURACY_BACKLOG.md`.

- Keep report and plugin regression coverage aligned with new configuration flags and output variants
- Continue tightening remaining rule-accuracy boundaries where the expected behavior is testable and low-noise
- Keep generated `rules-reference.md`, README guidance, and sample documentation aligned with the latest built-in rule surface
- Gather real-user feedback from bundle-first adoption before committing to broader rule expansion or external extension APIs
- Keep `v0.1.5` scoped as an accuracy-and-stability release rather than another broad rule-growth batch; see `docs/V0.1.5_CANDIDATE_SCOPE.md`

## Recently completed

- Configuration validation now warns on unknown report formats and empty core output selections
- Baseline and cache loading now stream file contents to reduce memory pressure
- Cache miss hints are surfaced in plugin logs when incremental cache is enabled but no entries are reused
- Governance-oriented outputs now include `rules-governance.json`
- Reactor scans now support module-level extra source roots
- File analysis now supports explicit concurrency controls instead of relying on the common fork-join pool
- JSON reporting now supports a lightweight output mode for large repositories
- The highest-value accuracy follow-ups for cache, endpoint security, self-invocation, and scheduled trigger parsing have been covered with regression tests and semantic-layer improvements
- The project now supports lightweight external rule discovery through `ServiceLoader`, along with an adoption-suite sample and plugin validation coverage
- Built-in proxy-boundary coverage was expanded across async, cache, and transactional event-listener scenarios with focused low-noise rules
- English and Chinese README files now include a rule coverage matrix, and generated rule docs now include a domain coverage snapshot

## Current remaining priorities

1. Continue refining `SPRING_CACHEABLE_KEY` only through explicit, low-noise project-convention signals
2. Revisit `SPRING_ENDPOINT_SECURITY` only for high-confidence centralized-policy hints; avoid speculative config parsing
3. Expand report and plugin regression coverage whenever generated docs, output fields, or configuration flags change
4. Keep samples, README guidance, and generated rule docs aligned with the current governance and rollout workflows
5. Prefer focused semantic improvements in existing domains over broad new rule families
6. Keep the next release candidate (`v0.1.5`) constrained to one or two explainable themes, ideally accuracy refinement plus release-pipeline stability

## Later candidates

- Better ergonomics around external rule packaging, diagnostics, and governance beyond the current lightweight `ServiceLoader` SPI
- More Spring runtime-semantics checks in adjacent areas such as scheduling, lifecycle, or event timing
- Additional report polish for SARIF and HTML consumption in CI-heavy environments

## Not the current priority

- Large one-shot feature batches
- Heavy framework additions or major architecture rewrites
- Broad language support beyond the current Java + Maven focus
