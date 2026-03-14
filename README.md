# spring-correctness-linter

[中文说明](README.zh-CN.md)

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
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0:lint \
  -Dspring.correctness.linter.writeBaseline=true
```

### Fail on severity

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0:lint \
  -Dspring.correctness.linter.failOnSeverity=WARNING
```

## Maven Usage

Example plugin configuration:

```xml
<plugin>
  <groupId>io.github.koyan9</groupId>
  <artifactId>spring-correctness-linter-maven-plugin</artifactId>
  <version>0.1.0</version>
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

This keeps cache reuse fast without silently reusing findings across materially different analysis configurations.

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

## Key Configuration Properties

List-valued properties accept comma or semicolon separators. Whitespace is trimmed.
Rule ids are normalized to uppercase. Rule domains are case-insensitive and accept `-` or spaces (for example `transaction`, `Transaction`, `TRANSACTION`).

| Property | Default | Values | Effect |
| --- | --- | --- | --- |
| `spring.correctness.linter.sourceDirectory` | `${project.basedir}/src/main/java` | Path (absolute or relative) | Overrides the primary source root. Relative paths are resolved against the project base directory. |
| `spring.correctness.linter.additionalSourceDirectories` | _empty_ | Paths separated by `,` or `;` | Adds extra source roots for the current module. Relative paths are resolved against the project base directory. |
| `spring.correctness.linter.scanReactorModules` | `false` | `true` / `false` | Scan the full Maven reactor from the execution root. Non-root modules are skipped when enabled. |
| `spring.correctness.linter.includeTestSourceRoots` | `false` | `true` / `false` | Include test compile source roots when resolving source roots. |
| `spring.correctness.linter.reportDirectory` | `${project.build.directory}/spring-correctness-linter` | Path | Report output directory. |
| `spring.correctness.linter.formats` | `json,html,sarif` | `json`, `html`, `sarif` | Controls which core report formats to write. Baseline diff and rule docs are controlled separately. |
| `spring.correctness.linter.baselineFile` | `${project.basedir}/spring-correctness-linter-baseline.txt` | Path | Baseline file path used for filtering and/or writing. |
| `spring.correctness.linter.honorInlineSuppressions` | `true` | `true` / `false` | Enables inline suppression comments. |
| `spring.correctness.linter.applyBaseline` | `true` | `true` / `false` | Applies baseline filtering to hide known issues. |
| `spring.correctness.linter.writeBaseline` | `false` | `true` / `false` | Writes a new baseline file (or per-module baseline files when splitting). |
| `spring.correctness.linter.writeBaselineDiff` | `true` | `true` / `false` | Writes `baseline-diff.json` and `baseline-diff.html` when a baseline path is configured. |
| `spring.correctness.linter.writeRuleDocs` | `true` | `true` / `false` | Writes the generated rule reference markdown. |
| `spring.correctness.linter.ruleDocsFileName` | `rules-reference.md` | File name or path | File name (or relative path) for the generated rules reference under the report directory. |
| `spring.correctness.linter.failOnSeverity` | _unset_ | `INFO`, `WARNING`, `ERROR` | Fails the build when any visible issue meets or exceeds the threshold. Takes precedence over `failOnError`. |
| `spring.correctness.linter.failOnError` | `false` | `true` / `false` | Fails the build when any visible issue remains, only when `failOnSeverity` is not set. |
| `spring.correctness.linter.enabledRules` | _empty_ | Rule IDs | Enables only the specified rule IDs. Unknown IDs fail the build. |
| `spring.correctness.linter.disabledRules` | _empty_ | Rule IDs | Disables the specified rule IDs. Unknown IDs fail the build. |
| `spring.correctness.linter.enabledRuleDomains` | _empty_ | `ASYNC`, `LIFECYCLE`, `SCHEDULED`, `CACHE`, `WEB`, `TRANSACTION`, `EVENTS`, `CONFIGURATION`, `GENERAL` | Enables only selected rule domains. Unknown domains fail the build. |
| `spring.correctness.linter.disabledRuleDomains` | _empty_ | Same as above | Disables selected rule domains. |
| `spring.correctness.linter.severityOverrides` | _empty_ | `RULE_ID=INFO|WARNING|ERROR` | Overrides per-rule severities. Unknown rule IDs fail the build. |
| `spring.correctness.linter.assumeCentralizedSecurity` | `false` | `true` / `false` | Skips `SPRING_ENDPOINT_SECURITY` when security is enforced centrally. |
| `spring.correctness.linter.securityAnnotations` | _empty_ | Annotation names | Treats additional annotations as explicit security intent. Accepts simple or fully qualified names (leading `@` is allowed). |
| `spring.correctness.linter.cacheDefaultKeyCacheNames` | _empty_ | Cache names or `*` | Allows default cache keys for specific cache names. `*` allows all caches. |
| `spring.correctness.linter.cacheFile` | `${project.build.directory}/spring-correctness-linter/analysis-cache.txt` | Path | Incremental cache file path (ignored when cache is disabled or split by module). |
| `spring.correctness.linter.useIncrementalCache` | `true` | `true` / `false` | Enables file-content-based incremental cache reuse. |
| `spring.correctness.linter.splitBaselineByModule` | `false` | `true` / `false` | Writes module-scoped baseline files under `modules/<module>/` next to the baseline file parent directory. |
| `spring.correctness.linter.splitCacheByModule` | `false` | `true` / `false` | Writes module-scoped cache files under `modules/<module>/` next to the cache file parent directory. |

### Centralized security intent

If endpoint security is enforced in infrastructure such as `SecurityFilterChain`, gateways, or sidecars, you can reduce noise by
disabling the endpoint-security rule globally or by providing your internal security annotations:

```xml
<configuration>
  <assumeCentralizedSecurity>true</assumeCentralizedSecurity>
  <securityAnnotations>InternalEndpoint,TeamSecure</securityAnnotations>
  <!-- Also supported: @InternalEndpoint or com.example.InternalEndpoint -->
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

PowerShell note: quote dotted `-Dspring.correctness.linter.*` properties or invoke Maven through `cmd /c`.

Available built-in rule domains currently include `ASYNC`, `LIFECYCLE`, `SCHEDULED`, `CACHE`, `WEB`, `TRANSACTION`, `EVENTS`, `CONFIGURATION`, and `GENERAL`.

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
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0:lint \
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

## Repository Structure

- `linter-core/`: core analysis engine and rules
- `linter-maven-plugin/`: Maven integration
- `samples/vulnerable-sample/`: single-module validation sample
- `samples/reactor-sample/`: reactor validation sample
- `CHANGELOG.md`: release history
- `RELEASE_NOTES_TEMPLATE.md`: release notes template
- `docs/RELEASE_PROCESS.md`: release checklist and workflow guide
- `docs/RULE_DEVELOPMENT.md`: rule implementation and semantic-facts guide
- `docs/ACCURACY_BACKLOG.md`: prioritized rule-accuracy and regression-test backlog

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
