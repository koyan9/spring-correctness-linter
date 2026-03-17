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
- Improve copy-paste-ready examples for CI quality gates, baseline rollout, and SARIF upload workflows

Current recommended adoption strategy:

- Start with a domain bundle before enabling the full default rule set
- Use `CI Starter` (`ASYNC,TRANSACTION,WEB`) as the default first pass for new projects
- Use `Transaction Focus` (`TRANSACTION,EVENTS`) when teams want to harden transaction and event timing semantics first
- Use `Web/API Focus` (`WEB`) for controller exposure and endpoint security intent reviews
- Use `Cache Focus` (`CACHE`) when validating cache behavior in isolation before broader rollout
- Use generated `rules-reference.md` to expand bundles into concrete rule IDs after the first rollout pass
- Validate `CI Starter`, transaction, web, and configuration-oriented adoption paths with `samples/vulnerable-sample/`
- Validate reactor/module grouping and async-heavy bundle behavior with `samples/reactor-sample/`

### 3. Maintainable rule growth

- Keep the default rule set stable while improving the developer experience for future rule additions
- Prefer improvements to rule metadata, test coverage, and report clarity before adding broad new rule families
- Preserve a low-friction path for future extensibility without disrupting current Maven-first usage

## Suggested next 1-2 iterations

Track rule-accuracy follow-ups and regression-test priorities in `docs/ACCURACY_BACKLOG.md`.

- Deepen runtime metrics where useful, especially around module-level timing summaries and large-repository diagnosis
- Continue tightening report wording, quality-gate messages, and sample-based verification flows
- Keep the recommended bundle guidance in README, `rules-reference.md`, and sample docs aligned as adoption patterns evolve
- Add small, high-confidence correctness checks only if they fit the current reporting and testing model cleanly
- Gather real-user feedback from sample-driven adoption before committing to broader rule expansion

## Planned improvements (priority order)

1. Validate configuration inputs and warn on unknown report formats or empty outputs
2. Stream baseline and cache loading to reduce memory pressure on large repositories
3. Surface cache invalidation hints in logs to explain cache-miss causes
4. Export a governance snapshot (rule status/severity) for audit workflows
5. Tackle the top items from `docs/ACCURACY_BACKLOG.md` with focused regression tests
6. Add module-level extra source root configuration for reactor scans
7. Evaluate safe per-file parallel analysis for performance gains
8. Add optional report-field toggles for lightweight outputs

## Later candidates

- Lightweight rule registration or extension mechanisms beyond the built-in rule list
- More Spring runtime-semantics checks in adjacent areas such as scheduling, lifecycle, or async/transaction interaction
- Additional report polish for SARIF and HTML consumption in CI-heavy environments

## Not the current priority

- Large one-shot feature batches
- Heavy framework additions or major architecture rewrites
- Broad language support beyond the current Java + Maven focus
