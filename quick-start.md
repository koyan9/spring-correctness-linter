# spring-correctness-linter Quick Start

This guide is for teams that want to integrate `spring-correctness-linter` into an existing Spring project with the least amount of trial and error.

It focuses on:

- how to add the Maven plugin to another project
- which commands to run first
- how to adopt baseline and cache safely
- which configuration sets fit common rollout stages
- how to move from local trial to CI enforcement

For the full reference of every property, see [README.md](README.md).

## 1. Preconditions

The current project is designed for:

- Java 17+
- Maven builds
- Spring projects where correctness depends on annotations, proxies, transactions, events, scheduling, cache behavior, and controller security intent

Recommended first validation target:

- a normal single-module Spring application with `src/main/java`

## 2. Add the Plugin

Add this plugin to the target project's `pom.xml`:

```xml
<plugin>
  <groupId>io.github.koyan9</groupId>
  <artifactId>spring-correctness-linter-maven-plugin</artifactId>
  <version>0.1.6</version>
  <configuration>
    <formats>
      <format>json</format>
      <format>html</format>
      <format>sarif</format>
    </formats>
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

If you are evaluating a local checkout of this repository before a public release, install artifacts first:

```bash
./mvnw -q -DskipTests install
```

Windows:

```powershell
.\mvnw.cmd -q -DskipTests install
```

## 3. First Run

Run the project with the plugin enabled but without a hard quality gate:

```bash
mvn -q verify
```

Inspect:

- `target/spring-correctness-linter/lint-report.json`
- `target/spring-correctness-linter/lint-report.html`
- `target/spring-correctness-linter/rules-reference.md`
- `target/spring-correctness-linter/rules-governance.json`

What to look for:

- which rule domains are active
- how many findings are visible
- whether parse problems are present
- whether the reported issues are real risks or rollout noise

## 4. Start Narrow, Not Wide

For most real projects, begin with a smaller rule surface:

```xml
<configuration>
  <enabledRuleDomains>ASYNC,TRANSACTION,WEB</enabledRuleDomains>
</configuration>
```

This `CI Starter` bundle is the best first pass when you want:

- proxy-boundary mistakes
- transaction pitfalls
- public endpoint security intent review

Useful focused bundles:

- `SCHEDULED`
- `LIFECYCLE`
- `TRANSACTION,EVENTS`
- `CACHE`
- `WEB`

## 5. Add a Baseline

If the project already has historical issues, baseline first.

Generate the baseline once:

```bash
mvn -q io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.6:lint \
  "-Dspring.correctness.linter.writeBaseline=true"
```

This writes:

- `spring-correctness-linter-baseline.txt`

Then keep this enabled in normal runs:

```xml
<configuration>
  <applyBaseline>true</applyBaseline>
</configuration>
```

Recommended practice:

- commit the baseline file
- review `baseline-diff.json` / `baseline-diff.html`
- only refresh the baseline intentionally

## 6. Enable Incremental Cache

Incremental cache should usually be on for both local and CI runs:

```xml
<configuration>
  <useIncrementalCache>true</useIncrementalCache>
</configuration>
```

Default cache file:

- `target/spring-correctness-linter/analysis-cache.txt`

Recommended CI usage:

- persist the cache directory between runs
- do not commit cache files to git

## 7. Add a Quality Gate

After the rule set is stable, enable a severity gate:

```xml
<configuration>
  <failOnSeverity>WARNING</failOnSeverity>
</configuration>
```

Or use the simpler fallback:

```xml
<configuration>
  <failOnError>true</failOnError>
</configuration>
```

Rule:

- `failOnSeverity` takes precedence over `failOnError`

## 8. Common Real-World Configurations

### Centralized security

If endpoint security is enforced outside controllers:

```xml
<configuration>
  <assumeCentralizedSecurity>true</assumeCentralizedSecurity>
</configuration>
```

Or enable detection of resolvable Spring Security chain beans, including component-scanned implementations:

```xml
<configuration>
  <autoDetectCentralizedSecurity>true</autoDetectCentralizedSecurity>
</configuration>
```

Or add your internal security annotations:

```xml
<configuration>
  <securityAnnotations>InternalEndpoint,TeamSecure</securityAnnotations>
</configuration>
```
<!-- Package and `@` prefixes are ignored when the plugin normalizes security annotations; only the simple name matters. -->

### Cache conventions

Allow specific caches to use Spring default keys:

```xml
<configuration>
  <cacheDefaultKeyCacheNames>users,orders</cacheDefaultKeyCacheNames>
</configuration>
```

If the project standardizes on a resolvable Spring global key generator, a component-scanned `KeyGenerator`, or a `CachingConfigurer` / `CachingConfigurerSupport` key generator:

```xml
<configuration>
  <autoDetectProjectWideKeyGenerator>true</autoDetectProjectWideKeyGenerator>
</configuration>
```

### Lightweight JSON output

If downstream tooling only needs summary-level JSON plus compact runtime diagnostics:

```xml
<configuration>
  <formats>
    <format>json</format>
  </formats>
  <lightweightReports>true</lightweightReports>
</configuration>
```

### Explicit concurrency control

If CI CPU usage must be capped:

```xml
<configuration>
  <parallelFileAnalysis>true</parallelFileAnalysis>
  <fileAnalysisParallelism>4</fileAnalysisParallelism>
</configuration>
```

To force serial file analysis:

```xml
<configuration>
  <parallelFileAnalysis>false</parallelFileAnalysis>
</configuration>
```

### Multi-module reactor

For a Maven reactor build:

```xml
<configuration>
  <scanReactorModules>true</scanReactorModules>
  <splitBaselineByModule>true</splitBaselineByModule>
  <splitCacheByModule>true</splitCacheByModule>
</configuration>
```

Only modules that actually contribute Java source files will appear in module reports and per-module outputs.

If some modules need extra source roots:

```xml
<configuration>
  <moduleSourceDirectories>root-app=src/it/java;module-a=src/generated/java</moduleSourceDirectories>
</configuration>
```

## 9. CI Setup

Minimal CLI:

```bash
mvn -B -q verify \
  "-Dspring.correctness.linter.applyBaseline=true" \
  "-Dspring.correctness.linter.failOnSeverity=WARNING"
```

GitHub Actions example:

```yaml
- name: Cache linter analysis
  uses: actions/cache@v4
  with:
    path: .cache/spring-correctness-linter
    key: ${{ runner.os }}-linter-${{ hashFiles('**/pom.xml') }}

- name: Verify project
  run: mvn -B -q verify "-Dspring.correctness.linter.cacheFile=.cache/spring-correctness-linter/analysis-cache.txt"
```

## 10. Recommended Rollout Order

1. Add the plugin with reports only
2. Run locally without a gate
3. Narrow to a starter bundle if noise is high
4. Generate and commit a baseline
5. Enable incremental cache
6. Enable a severity gate
7. Expand enabled rule domains gradually
8. Raise high-signal rules with `severityOverrides`

## 11. What to Inspect After Each Run

- `lint-report.html` for human review
- `lint-report.json` for automation
- `rules-governance.json` to confirm active rules and severities
- `baseline-diff.*` to understand new vs matched vs stale issues

## 12. Common Problems

If the plugin runs but you see no outputs:

- check `formats`
- check `reportDirectory`
- verify the goal is bound or invoked explicitly

If the build fails unexpectedly:

- verify `failOnSeverity` / `failOnError`
- inspect `rules-governance.json`
- check whether baseline filtering is applied

If cache hit rate stays at zero:

- verify the cache file path is stable
- verify CI restores the cache directory
- remember rule, semantic-option, and project-context changes invalidate cache safely

If too many endpoint-security findings appear:

- use `assumeCentralizedSecurity=true`
- or `autoDetectCentralizedSecurity=true`
- or register internal security annotations

If too many cache-key findings appear:

- allow explicit cache names
- or opt into project-wide key generator detection if that convention truly exists

## 13. Sample Projects

Use the built-in samples as references:

- `samples/vulnerable-sample/`
- `samples/reactor-sample/`
- `samples/adoption-suite/basic-app/`
- `samples/adoption-suite/centralized-security-app/`
- `samples/adoption-suite/cache-convention-app/`

Use `samples/adoption-suite/` when you want to validate consumer-style rollout paths such as centralized security and project-wide cache-key conventions.

## 14. Related Docs

- [README.md](README.md)
- [README.zh-CN.md](README.zh-CN.md)
- [docs/RULE_DEVELOPMENT.md](docs/RULE_DEVELOPMENT.md)
- [docs/ACCURACY_BACKLOG.md](docs/ACCURACY_BACKLOG.md)
- [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md)
- [docs/CI_EXAMPLES.md](docs/CI_EXAMPLES.md)

