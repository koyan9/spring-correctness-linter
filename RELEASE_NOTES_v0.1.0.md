# spring-correctness-linter v0.1.0

## Highlights

- First public release of `spring-correctness-linter`
- AST-based Spring correctness checks for common proxy, transactional, async, cache, and controller-security pitfalls
- JSON / HTML / SARIF report output
- Inline suppression, baseline, baseline diff, and severity-based quality gate

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
- Sample project for local validation

## Verification

- `./mvnw -q verify`