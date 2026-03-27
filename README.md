# spring-correctness-linter

[中文说明](README.zh-CN.md)

Detailed adoption guide: [quick-start.md](quick-start.md)

`spring-correctness-linter` is a Maven-first static analysis tool for Spring applications. It focuses on **correctness and runtime semantics**, not general Java style. The project is designed to catch Spring-specific problems that compile successfully but often fail later in CI, integration testing, or production.

## Highlights

- Spring correctness checks instead of general Java style linting
- AST-based analysis for proxy, transaction, async, event, cache, and controller-security risks
- Baseline-friendly adoption for legacy codebases
- Severity-based CI quality gates with module-aware failure messages
- Multi-source-root and Maven reactor support for large repositories
- JSON / HTML / SARIF / baseline diff output for local, CI, and code scanning workflows

## Why This Project Exists

Spring applications can hide risky behavior behind annotations and proxy-based runtime semantics. A codebase may look valid while still containing defects such as:

- `@Async` methods that cannot be advised correctly
- `@Transactional` methods that lose proxy interception because of visibility, `final`, or self-invocation
- event listeners with unclear transaction boundaries
- cache annotations that create ambiguous or unsafe cache behavior
- public controller endpoints without explicit security intent
- contradictory conditional bean definitions

The goal of this project is to move those checks **earlier** into local development and CI.

## Expected Outcomes

With this linter in place, teams can:

- catch Spring runtime risks before deployment
- adopt a baseline and focus only on newly introduced problems
- enforce severity-based quality gates in CI
- inspect results in JSON, HTML, SARIF, and baseline diff reports
- scale checks from a single module to multi-module Maven reactors

## Architecture

The repository is split into two main modules:

- `linter-core/`: AST-based analysis engine, rule model, report generation, baseline handling, incremental cache, and module-aware aggregation
- `linter-maven-plugin/`: Maven plugin that resolves source roots, applies configuration, runs the core engine, and writes reports during `verify`

Supporting directories:

- `samples/vulnerable-sample/`: single-module sample for baseline, reports, and common rule behavior
- `samples/reactor-sample/`: multi-module reactor sample for module-aware scanning and per-module outputs
- `samples/adoption-suite/`: consumer-style sample apps that demonstrate realistic plugin adoption patterns

## Analysis Flow

At a high level, one lint run works like this:

1. Resolve source roots from the current module, extra configured roots, or the whole Maven reactor.
2. Load source files and compute content hashes for incremental cache reuse.
3. Parse Java source into AST-backed structures and collect parse diagnostics when syntax is broken.
4. Run the enabled rules over each file.
5. Apply inline suppression rules.
6. Apply baseline filtering.
7. Aggregate findings by severity and by module.
8. Evaluate quality gates.
9. Write reports and optional baseline/cache files.

## Rule Scope

The default rule set currently focuses on:

- `@Async` misuse (private/final methods, self-invocation)
- lifecycle and startup boundary reviews such as `@PostConstruct`, `afterPropertiesSet()`, `ApplicationRunner`, and `SmartInitializingSingleton` with proxy-oriented annotations
- `@Scheduled` misuse and scheduling boundary reviews
- `@Transactional` misuse (private/final methods, final classes, self-invocation, high-risk propagation)
- `@EventListener` / `@TransactionalEventListener` boundaries
- cache key and cache annotation combination risks
- controller security intent checks
- conditional bean conflict detection

Some rules that need type resolution (for example `SPRING_TX_SELF_INVOCATION`) use a conservative lookup strategy: same-package matches first, then explicit or wildcard imports, and finally unique simple-name matches when unambiguous. The shared implementation lives in `TypeResolutionIndex` under `linter-core/`. See `docs/RULE_DEVELOPMENT.md` for the current resolution guidance.

## Quick Start

For a scenario-oriented, step-by-step adoption guide, see [quick-start.md](quick-start.md).

### Build and verify

```bash
./mvnw -q verify
```

### Install local artifacts

```bash
./mvnw -q -DskipTests install
```

### Generate a baseline

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.3:lint \
  -Dspring.correctness.linter.writeBaseline=true
```

### Fail on severity

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.3:lint \
  -Dspring.correctness.linter.failOnSeverity=WARNING
```

## Maven Usage

Example plugin configuration:

```xml
<plugin>
  <groupId>io.github.koyan9</groupId>
  <artifactId>spring-correctness-linter-maven-plugin</artifactId>
  <version>0.1.3</version>
  <configuration>
    <formats>
      <format>json</format>
      <format>html</format>
      <format>sarif</format>
    </formats>
    <failOnSeverity>WARNING</failOnSeverity>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>lint</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

## Main Outputs

Reports are written under `target/spring-correctness-linter/` by default:

- `lint-report.json`
- `lint-report.html`
- `lint-report.sarif.json`
- `baseline-diff.json`
- `baseline-diff.html`
- `rules-reference.md`

When module splitting is enabled, module-specific files can also be written under `modules/<module>/`.

JSON and HTML reports now also include runtime metrics so users can inspect:

- total analysis duration
- analyzed vs cached file counts
- cache hit rate and cache scope
- cache miss reasons when reuse is skipped due to context or configuration changes
- per-phase timing breakdown
- per-module analysis timing and cache hit rate
- per-module analyzed vs cached time (analyzedMillis/cachedMillis)
- slowest modules by analyzed time
- configured enabled / disabled rule domains and effective rule domains for the current run
- configured enabled / disabled rule ids and an effective per-domain rule breakdown for the current run

## Baseline and Incremental Cache

The project supports two complementary workflows:

- **Baseline**: accept the current known issues and focus on newly introduced problems
- **Incremental cache**: skip re-analysis for unchanged files and reuse prior results safely

Available patterns:

- single baseline file
- per-module baseline files
- single incremental cache file
- per-module incremental cache files

Incremental cache reuse is invalidated automatically when the effective analysis fingerprint changes. In practice that fingerprint includes:

- the enabled rule set
- per-rule severity overrides
- inline suppression behavior
- the current analysis engine implementation
- semantic options such as centralized-security and project-wide key-generator detection
- source-derived semantic context such as annotation definitions, type-resolution summaries, and source-root composition

This keeps cache reuse fast without silently reusing findings across materially different analysis configurations or project semantic contexts.

## Multi-Module and Reactor Support

The plugin can scan:

- a single module
- multiple source roots inside one module
- an entire Maven reactor from the execution root

When reactor scanning is enabled:

- compile source roots from all reactor modules can be collected
- reports group findings by module
- quality gate failures mention the affected module IDs
- baseline and cache files can be split by module
- only modules that actually contribute Java source files appear in module reports and per-module outputs

## Key Configuration Properties

List-valued properties accept comma or semicolon separators. Whitespace is trimmed.
Rule ids are normalized to uppercase. Rule domains are case-insensitive and accept `-` or spaces (for example `transaction`, `Transaction`, `TRANSACTION`).

| Property | Default | Values | Effect |
| --- | --- | --- | --- |
| `spring.correctness.linter.sourceDirectory` | `${project.basedir}/src/main/java` | Path (absolute or relative) | Overrides the primary source root. Relative paths are resolved against the project base directory. |
| `spring.correctness.linter.additionalSourceDirectories` | _empty_ | Paths separated by `,` or `;` | Adds extra source roots for the current module. Relative paths are resolved against the project base directory. |
| `spring.correctness.linter.moduleSourceDirectories` | _empty_ | `moduleId=path1,path2;moduleId2=path3` | Adds per-module extra source roots. Paths are resolved against each module’s base directory. Unknown module ids fail the build. |
| `spring.correctness.linter.scanReactorModules` | `false` | `true` / `false` | Scan the full Maven reactor from the execution root. Non-root modules are skipped when enabled. |
| `spring.correctness.linter.includeTestSourceRoots` | `false` | `true` / `false` | Include test compile source roots when resolving source roots. |
| `spring.correctness.linter.reportDirectory` | `${project.build.directory}/spring-correctness-linter` | Path | Report output directory. |
| `spring.correctness.linter.formats` | `json,html,sarif` | `json`, `html`, `sarif` | Controls which core report formats to write. Values are case-insensitive; unknown values are ignored with a warning. Baseline diff and rule docs are controlled separately. |
| `spring.correctness.linter.baselineFile` | `${project.basedir}/spring-correctness-linter-baseline.txt` | Path | Baseline file path used for filtering and/or writing. |
| `spring.correctness.linter.honorInlineSuppressions` | `true` | `true` / `false` | Enables inline suppression comments. |
| `spring.correctness.linter.applyBaseline` | `true` | `true` / `false` | Applies baseline filtering to hide known issues. |
| `spring.correctness.linter.writeBaseline` | `false` | `true` / `false` | Writes a new baseline file (or per-module baseline files when splitting). |
| `spring.correctness.linter.writeBaselineDiff` | `true` | `true` / `false` | Writes `baseline-diff.json` and `baseline-diff.html` when a baseline path is configured. |
| `spring.correctness.linter.writeRuleDocs` | `true` | `true` / `false` | Writes both `rules-reference.md` and the `rules-governance.json` snapshot (governance/audit workflows rely on this file). |
| `spring.correctness.linter.ruleDocsFileName` | `rules-reference.md` | File name or path | File name (or relative path) for the generated rules reference under the report directory. |
| `spring.correctness.linter.lightweightReports` | `false` | `true` / `false` | Writes a lightweight `lint-report.json` (summary + rule selection + compact runtime summary) to reduce report size. HTML/SARIF outputs are unchanged. |
| `spring.correctness.linter.failOnSeverity` | _unset_ | `INFO`, `WARNING`, `ERROR` | Fails the build when any visible issue meets or exceeds the threshold. Takes precedence over `failOnError`. |
| `spring.correctness.linter.failOnError` | `false` | `true` / `false` | Fails the build when any visible issue remains, only when `failOnSeverity` is not set. |
| `spring.correctness.linter.enabledRules` | _empty_ | Rule IDs | Enables only the specified rule IDs. Unknown IDs fail the build. |
| `spring.correctness.linter.disabledRules` | _empty_ | Rule IDs | Disables the specified rule IDs. Unknown IDs fail the build. |
| `spring.correctness.linter.enabledRuleDomains` | _empty_ | `ASYNC`, `LIFECYCLE`, `SCHEDULED`, `CACHE`, `WEB`, `TRANSACTION`, `EVENTS`, `CONFIGURATION` | Enables only selected rule domains. Unknown domains fail the build (`GENERAL` is accepted but no bundled rule uses it today). |
| `spring.correctness.linter.disabledRuleDomains` | _empty_ | Same as above | Disables selected rule domains. |
| `spring.correctness.linter.severityOverrides` | _empty_ | `RULE_ID=INFO|WARNING|ERROR` | Overrides per-rule severities. Unknown rule IDs fail the build. |
| `spring.correctness.linter.assumeCentralizedSecurity` | `false` | `true` / `false` | Skips `SPRING_ENDPOINT_SECURITY` when security is enforced centrally. |
| `spring.correctness.linter.autoDetectCentralizedSecurity` | `false` | `true` / `false` | Auto-skips `SPRING_ENDPOINT_SECURITY` when a resolvable Spring `SecurityFilterChain` or `SecurityWebFilterChain` bean is detected in the source tree. |
| `spring.correctness.linter.securityAnnotations` | _empty_ | Annotation names | Treats additional annotations as explicit security intent. Input values are normalized to simple annotation names (leading `@` and package prefixes are ignored), so the same simple name in different packages cannot be distinguished. |
| `spring.correctness.linter.cacheDefaultKeyCacheNames` | _empty_ | Cache names or `*` | Allows default cache keys for specific cache names. `*` allows all caches. |
| `spring.correctness.linter.autoDetectProjectWideKeyGenerator` | `false` | `true` / `false` | Auto-skips `SPRING_CACHEABLE_KEY` when a resolvable Spring `@Bean KeyGenerator` or `CachingConfigurer` / `CachingConfigurerSupport` key generator is detected in the source tree. |
| `spring.correctness.linter.cacheFile` | `${project.build.directory}/spring-correctness-linter/analysis-cache.txt` | Path | Incremental cache file path (ignored when cache is disabled or split by module). |
| `spring.correctness.linter.useIncrementalCache` | `true` | `true` / `false` | Enables incremental cache reuse guarded by file content plus the effective semantic analysis fingerprint. |
| `spring.correctness.linter.parallelFileAnalysis` | `true` | `true` / `false` | Enables per-file parallel analysis when multiple source files are present. |
| `spring.correctness.linter.fileAnalysisParallelism` | `0` | Integer `>= 0` | Caps file-analysis worker count. `0` means auto-detect from available processors. |
| `spring.correctness.linter.splitBaselineByModule` | `false` | `true` / `false` | Writes module-scoped baseline files under `modules/<module>/` next to the baseline file parent directory. |
| `spring.correctness.linter.splitCacheByModule` | `false` | `true` / `false` | Writes module-scoped cache files under `modules/<module>/` next to the cache file parent directory. |

### Centralized security intent

If endpoint security is enforced in infrastructure such as `SecurityFilterChain`, `SecurityWebFilterChain`, gateways, or sidecars, you can reduce noise by
disabling the endpoint-security rule globally or by providing your internal security annotations:

```xml
<configuration>
  <assumeCentralizedSecurity>true</assumeCentralizedSecurity>
  <!-- Or detect SecurityFilterChain / SecurityWebFilterChain automatically -->
  <!-- <autoDetectCentralizedSecurity>true</autoDetectCentralizedSecurity> -->
  <securityAnnotations>InternalEndpoint,TeamSecure</securityAnnotations>
  <!-- Values are normalized to simple annotation names, so package prefixes are ignored. -->
</configuration>
```

### Allow default cache keys for specific caches

If some caches intentionally rely on Spring's default key generation, you can allow them by name:

```xml
<configuration>
  <cacheDefaultKeyCacheNames>users,orders</cacheDefaultKeyCacheNames>
  <!-- Use '*' to allow default keys for all caches -->
</configuration>
```

If the project standardizes on a global `KeyGenerator` bean or a `CachingConfigurer` / `CachingConfigurerSupport` key generator, you can opt into auto-detection:

```xml
<configuration>
  <autoDetectProjectWideKeyGenerator>true</autoDetectProjectWideKeyGenerator>
</configuration>
```

PowerShell note: quote dotted `-Dspring.correctness.linter.*` properties or invoke Maven through `cmd /c`.

Available built-in rule domains currently include `ASYNC`, `LIFECYCLE`, `SCHEDULED`, `CACHE`, `WEB`, `TRANSACTION`, `EVENTS`, and `CONFIGURATION`; `GENERAL` is accepted but not populated by any bundled rule today.

Recommended starter bundles:

- `CI Starter`: `ASYNC,TRANSACTION,WEB`
- `Lifecycle Focus`: `LIFECYCLE`
- `Scheduled Focus`: `SCHEDULED`
- `Transaction Focus`: `TRANSACTION,EVENTS`
- `Web/API Focus`: `WEB`
- `Cache Focus`: `CACHE`

The generated `rules-reference.md` expands these bundles into concrete rule IDs, so teams can start with a domain bundle and refine later.

## Recommended Configurations

### Minimal local adoption

```xml
<configuration>
  <formats>
    <format>json</format>
    <format>html</format>
  </formats>
</configuration>
```

### CI quality gate

```xml
<configuration>
  <formats>
    <format>json</format>
    <format>html</format>
    <format>sarif</format>
  </formats>
  <failOnSeverity>WARNING</failOnSeverity>
</configuration>
```

### Recommended starter bundles

Use these when you want a smaller rollout surface before enabling the full default rule set.

#### CI Starter

```xml
<configuration>
  <enabledRuleDomains>ASYNC,TRANSACTION,WEB</enabledRuleDomains>
  <failOnSeverity>WARNING</failOnSeverity>
</configuration>
```

#### Scheduled Focus

```xml
<configuration>
  <enabledRuleDomains>SCHEDULED</enabledRuleDomains>
</configuration>
```

#### Lifecycle Focus

```xml
<configuration>
  <enabledRuleDomains>LIFECYCLE</enabledRuleDomains>
</configuration>
```

#### Transaction Focus

```xml
<configuration>
  <enabledRuleDomains>TRANSACTION,EVENTS</enabledRuleDomains>
</configuration>
```

#### Web/API Focus

```xml
<configuration>
  <enabledRuleDomains>WEB</enabledRuleDomains>
</configuration>
```

#### Cache Focus

```xml
<configuration>
  <enabledRuleDomains>CACHE</enabledRuleDomains>
</configuration>
```

### Baseline-first rollout for legacy code

```xml
<configuration>
  <writeBaseline>false</writeBaseline>
  <applyBaseline>true</applyBaseline>
  <useIncrementalCache>true</useIncrementalCache>
</configuration>
```

Generate the first baseline once with:

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.3:lint \
  "-Dspring.correctness.linter.writeBaseline=true"
```

### Multi-module reactor governance

```xml
<configuration>
  <scanReactorModules>true</scanReactorModules>
  <splitBaselineByModule>true</splitBaselineByModule>
  <splitCacheByModule>true</splitCacheByModule>
  <useIncrementalCache>true</useIncrementalCache>
</configuration>
```

## Adoption Playbook

### Typical rollout flow

1. Start locally with a focused bundle such as `ASYNC,TRANSACTION,WEB` and keep `failOnSeverity` unset.
2. Generate a baseline once and commit it to version control.
3. Enable CI with `applyBaseline=true` and `useIncrementalCache=true` to focus on new issues only.
4. Gradually expand rule domains or enable the full default set once noise is under control.
5. Promote high-signal rules to `ERROR` via `severityOverrides` and tighten the CI gate over time.

### CI/CD configuration

Minimal Maven CLI example:

```bash
mvn -B -q verify \
  "-Dspring.correctness.linter.applyBaseline=true" \
  "-Dspring.correctness.linter.failOnSeverity=WARNING"
```

GitHub Actions example (with cache reuse):

```yaml
- name: Cache linter analysis
  uses: actions/cache@v4
  with:
    path: .cache/spring-correctness-linter
    key: ${{ runner.os }}-linter-${{ hashFiles('**/pom.xml') }}

- name: Verify project
  run: mvn -B -q verify "-Dspring.correctness.linter.cacheFile=.cache/spring-correctness-linter/analysis-cache.txt"
```

GitLab CI example:

```yaml
lint:
  stage: test
  cache:
    key: "${CI_COMMIT_REF_SLUG}"
    paths:
      - .cache/spring-correctness-linter
  script:
    - mvn -B -q verify "-Dspring.correctness.linter.cacheFile=.cache/spring-correctness-linter/analysis-cache.txt"
```

### Baseline strategy

1. Generate the first baseline:
   `./mvnw spring-correctness-linter:lint "-Dspring.correctness.linter.writeBaseline=true"`
2. Commit the baseline file (or per-module baseline files when splitting).
3. Keep `applyBaseline=true` in CI to suppress known issues.
4. Periodically refresh the baseline and review `baseline-diff.*` to ensure the set of known issues only shrinks.
5. For reactors, prefer `splitBaselineByModule=true` so each module owns its own baseline file.

### Incremental cache best practices

- Keep `useIncrementalCache=true` for local and CI runs.
- Place `cacheFile` under a stable cache directory and persist it in CI, rather than committing it to git.
- Use `splitCacheByModule=true` for reactor scans to avoid cross-module churn.
- Cache reuse is automatically invalidated when rule configuration, semantic options, source-root composition, or source-derived semantic context changes, so it is safe to keep enabled.

### Rule governance suggestions

- Start with domain bundles and add new domains only after the current set stabilizes.
- Use `severityOverrides` to raise high-risk rules to `ERROR` while keeping others at `WARNING`.
- Track any `disabledRules` or `disabledRuleDomains` in a backlog and review them regularly.
- Prefer inline suppressions with explicit reasons over broad domain disables.
- Review rule-reference docs after upgrades to see newly added rules and adjust your governance plan.

## Parameter Examples

Only JSON output, custom report directory, no rule docs:

```xml
<configuration>
  <reportDirectory>${project.build.directory}/lint</reportDirectory>
  <formats>
    <format>json</format>
  </formats>
  <lightweightReports>true</lightweightReports>
  <writeRuleDocs>false</writeRuleDocs>
</configuration>
```
This also disables the generated `rules-governance.json` snapshot.

Run a narrow rule set with overrides:

```xml
<configuration>
  <enabledRules>SPRING_ASYNC_VOID,SPRING_TX_SELF_INVOCATION</enabledRules>
  <severityOverrides>SPRING_ASYNC_VOID=ERROR</severityOverrides>
  <failOnSeverity>WARNING</failOnSeverity>
</configuration>
```

Enable domain bundles and disable one noisy rule:

```xml
<configuration>
  <enabledRuleDomains>ASYNC,TRANSACTION,WEB</enabledRuleDomains>
  <disabledRules>SPRING_ENDPOINT_SECURITY</disabledRules>
</configuration>
```

Reactor scan with per-module baseline/cache:

```xml
<configuration>
  <scanReactorModules>true</scanReactorModules>
  <splitBaselineByModule>true</splitBaselineByModule>
  <splitCacheByModule>true</splitCacheByModule>
  <useIncrementalCache>true</useIncrementalCache>
</configuration>
```

Control file-analysis concurrency explicitly:

```xml
<configuration>
  <parallelFileAnalysis>true</parallelFileAnalysis>
  <fileAnalysisParallelism>4</fileAnalysisParallelism>
</configuration>
```

Add per-module extra source roots:

```xml
<configuration>
  <moduleSourceDirectories>root-app=src/it/java;module-a=src/generated/java</moduleSourceDirectories>
</configuration>
```

Allow default cache keys for selected caches:

```xml
<configuration>
  <cacheDefaultKeyCacheNames>users,orders</cacheDefaultKeyCacheNames>
</configuration>
```

Treat custom security annotations as explicit intent:

```xml
<configuration>
  <securityAnnotations>InternalEndpoint,TeamSecure</securityAnnotations>
</configuration>
```
<!-- Use the simple names shown above; package prefixes are stripped when the plugin normalizes the list. -->

## FAQ

Q: Why do I see “Unknown rule id(s)” failures?
A: Rule ids are validated and normalized to uppercase. Verify the ID exists in `rules-reference.md`.

Q: I set `failOnError=true`, but the build still passes.
A: If `failOnSeverity` is configured, it takes precedence. Remove it or set a lower threshold.

Q: Baseline is not hiding issues.
A: Ensure `applyBaseline=true` and the baseline file path matches where the baseline was generated.

Q: Cache hit rate stays at 0%.
A: Make sure `useIncrementalCache=true` and `cacheFile` points to a stable path that is persisted in CI.

Q: Why are some findings missing?
A: Parse problems can cause partial analysis. Check the report for parse problem sections.

## Report Interpretation

Generated outputs (by default under `target/spring-correctness-linter/`):

- `lint-report.json` and `lint-report.html`: findings, severities, module grouping, and runtime metrics.
- `lint-report.sarif.json`: SARIF output for code scanning.
- `baseline-diff.json` / `baseline-diff.html`: new vs matched vs stale baseline entries.
- `rules-reference.md`: rule metadata and guidance for each rule.
- `rules-governance.json`: snapshot of enabled rules, domains, and severities for audit/governance workflows.

Key runtime metrics to watch:

- `totalElapsedMillis`, `sourceFileCount`, `analyzedFileCount`, `cachedFileCount`
- cache scope and hit rate
- `cacheMissReasons` when cache reuse is skipped
- per-module analyzed time and cache hit rate (for reactor scans)

## Rule Governance Template

Use this lightweight template to track rule decisions:

| Rule ID | Domain | Severity | Status | Rationale | Owner | Review Date |
| --- | --- | --- | --- | --- | --- | --- |
| SPRING_TX_SELF_INVOCATION | TRANSACTION | WARNING | Enabled | High-risk proxy bypass | Platform | 2026-06-30 |

## CI Cache Strategies

GitHub Actions (cache with restore keys):

```yaml
- name: Cache linter analysis
  uses: actions/cache@v4
  with:
    path: .cache/spring-correctness-linter
    key: ${{ runner.os }}-linter-${{ hashFiles('**/pom.xml') }}
    restore-keys: |
      ${{ runner.os }}-linter-

- name: Verify project
  run: mvn -B -q verify "-Dspring.correctness.linter.cacheFile=.cache/spring-correctness-linter/analysis-cache.txt"
```

GitLab CI (cache per branch):

```yaml
lint:
  stage: test
  cache:
    key: "linter-${CI_COMMIT_REF_SLUG}"
    paths:
      - .cache/spring-correctness-linter
  script:
    - mvn -B -q verify "-Dspring.correctness.linter.cacheFile=.cache/spring-correctness-linter/analysis-cache.txt"
```

## Inline Suppression

Preferred syntax:

```java
// spring-correctness-linter:disable-next-line RULE_ID reason: explanation
```

Supported scopes:

- `disable-file`
- `disable-line`
- `disable-next-line`
- `disable-next-method`
- `disable-next-type`

Legacy `medical-linter:` prefixes remain accepted for compatibility.

## Reports and Quality Gates

Current reports include:

- visible findings
- parse problem visibility
- cached file count
- module summaries
- baseline diff summaries

Current quality gates support:

- severity thresholds
- module-aware failure messages

`failOnSeverity` takes precedence when it is configured. `failOnError=true` remains useful as a simple fallback when you want any visible issue to fail the build.

## GitHub Code Scanning

When SARIF output is enabled, GitHub Actions can upload the generated report to code scanning:

```yaml
- name: Verify project
  run: mvn -B -q verify

- name: Upload SARIF
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: target/spring-correctness-linter/lint-report.sarif.json
```

For multi-module reactors scanned from the execution root, upload the SARIF file generated in the reactor root report directory.
SARIF results also include a `moduleId` property so code scanning views can be grouped by module when needed.

## Validation and Coverage

Recommended validation flow:

1. Run `./mvnw -q verify`
2. Re-run the affected sample:
   - `samples/vulnerable-sample/` for single-module, report, suppression, and baseline changes
   - `samples/reactor-sample/` for reactor, multi-root, module grouping, and per-module baseline/cache changes
   - `samples/adoption-suite/` for consumer-style plugin adoption, centralized security, and project-wide cache-key convention changes
3. Run `./mvnw -q -DskipTests install` before release-sensitive or sample-installation-sensitive changes

Coverage:

- `./mvnw verify` generates module JaCoCo reports under each module’s `target/site/jacoco/`
- `./mvnw verify` also generates an aggregate JaCoCo report under `target/site/jacoco-aggregate/`
- `linter-core` currently enforces a minimum JaCoCo line coverage ratio of `85%`
- `linter-maven-plugin` currently enforces a minimum JaCoCo line coverage ratio of `75%`

## Samples

### `samples/vulnerable-sample/`

A single-module sample with intentionally risky Spring patterns. Use it to inspect baseline generation, report output, and individual rule behavior.

### `samples/reactor-sample/`

A multi-module Maven reactor sample that demonstrates:

- reactor-wide scanning
- module summaries in reports
- per-module baseline output
- per-module incremental cache output
- filtering out aggregator-only modules that do not contribute Java source files

### `samples/adoption-suite/`

A set of small consumer-style applications that demonstrate how real projects can adopt the plugin with:

- baseline and report generation
- centralized-security auto-detection
- project-wide cache key conventions
- explicit CI verification of these consumer-style adoption paths

## Repository Structure

- `linter-core/`: core analysis engine and rules
- `linter-maven-plugin/`: Maven integration
- `samples/vulnerable-sample/`: single-module validation sample
- `samples/reactor-sample/`: reactor validation sample
- `samples/adoption-suite/`: adoption-oriented sample suite
- `CHANGELOG.md`: release history
- `RELEASE_NOTES_TEMPLATE.md`: release notes template
- `docs/RELEASE_PROCESS.md`: release checklist and workflow guide
- `docs/CI_EXAMPLES.md`: copy-paste-ready CI quality-gate, baseline-rollout, and SARIF upload examples
- `docs/PERFORMANCE_BENCHMARKING.md`: repeatable cache and runtime benchmarking workflow
- `docs/MAINTAINER_GUIDE.md`: branch protection, release ownership, and maintainer operations
- `docs/RULE_DEVELOPMENT.md`: rule implementation and semantic-facts guide
- `docs/ACCURACY_BACKLOG.md`: prioritized rule-accuracy and regression-test backlog
- `quick-start.md`: detailed adoption and rollout guide

## Current Status

The project currently provides a practical Spring correctness linting workflow suitable for:

- local development feedback
- CI quality gates
- legacy codebase onboarding with baselines
- multi-module repository governance with module-aware reporting

## Milestones

- For a compact history of the major improvements and project evolution, see `docs/MILESTONES.md`.
- For the current near-term direction, see `docs/ROADMAP.md`.
- For the current rule-accuracy follow-ups, see `docs/ACCURACY_BACKLOG.md`.
