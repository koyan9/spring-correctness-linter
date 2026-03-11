# spring-correctness-linter v0.1.0

## Highlights

- First public release of `spring-correctness-linter`
- AST-based Spring correctness checks for common proxy, transactional, async, cache, and controller-security pitfalls
- JSON / HTML / SARIF report output
- Inline suppression, baseline, baseline diff, and severity-based quality gate
- Multi-source-root and Maven reactor scanning for larger repositories
- Incremental cache reuse with runtime observability in reports and Maven logs

## Included Rule Areas

- `@Async` misuse
- `@Transactional` misuse
- `@EventListener` / `@TransactionalEventListener` boundaries
- Cache key and cache annotation combination risks
- Controller security intent checks
- Conditional bean annotation conflicts

## Tooling

- Maven plugin: `spring-correctness-linter-maven-plugin`
- Generated rule reference support
- Single-module and reactor sample projects for local validation
- Module-aware JSON / HTML reporting and per-module baseline / cache output

## Reports and CI

- JSON and HTML reports include cache hit counts, analyzed file counts, phase timings, and per-module timing summaries
- SARIF output is ready for GitHub code scanning upload workflows
- Quality gate failures identify the affected module IDs in multi-module scans

## Upgrade and Adoption Notes

- Recommended configurations are documented for local adoption, CI quality gates, baseline-first rollout, and reactor-wide governance
- Incremental cache reuse is invalidated automatically when the effective analysis fingerprint changes, including rule selection and severity override changes
- PowerShell users should quote dotted `-Dspring.correctness.linter.*` properties or invoke Maven through `cmd /c`

## Verification

- `./mvnw -q verify`
- `./mvnw -q -f samples/vulnerable-sample/pom.xml -DskipTests verify`
- `./mvnw -q -f samples/reactor-sample/pom.xml -DskipTests verify`
