# spring-correctness-linter

`spring-correctness-linter` is a CI-first Spring correctness linter.

## Coordinates

- `io.github.koyan9:spring-correctness-linter-parent:0.1.0`
- `io.github.koyan9:spring-correctness-linter-core:0.1.0`
- `io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0`

## Capabilities

- AST-based Java source analysis
- JSON / HTML / SARIF reports
- Parse problem visibility in reports and plugin logs
- Inline suppression with reason and scope support
- Baseline and baseline diff
- Severity-based quality gates
- Auto-generated rule reference markdown

## Repo Layout

- `linter-core/`
- `linter-maven-plugin/`
- `samples/vulnerable-sample/`
- `samples/reactor-sample/`
- `CHANGELOG.md`
- `RELEASE_NOTES_TEMPLATE.md`
- `RELEASE_NOTES_v*.md`

## Commands

- Verify project: `./mvnw -q verify`
- Install local artifacts: `./mvnw -q -DskipTests install`
- Generate baseline: `./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0:lint -Dspring.correctness.linter.writeBaseline=true`
- Fail on severity: `./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0:lint -Dspring.correctness.linter.failOnSeverity=WARNING`

## Validation

- Core regression: `./mvnw -q verify`
- Single-module sample: `./mvnw -q -f samples/vulnerable-sample/pom.xml -DskipTests verify`
- Reactor sample: `./mvnw -q -f samples/reactor-sample/pom.xml -DskipTests verify`
- Release preflight: `./mvnw -q -DskipTests install`

Recommended pre-merge sequence:

1. Run `./mvnw -q verify`
2. Re-run the affected sample:
   - `samples/vulnerable-sample/` for report, baseline, or single-module changes
   - `samples/reactor-sample/` for module scanning, per-module baseline/cache, or reactor changes
3. Run `./mvnw -q -DskipTests install` before release or sample verification changes

## Configuration

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
</plugin>
```

Useful user properties:

- `spring.correctness.linter.sourceDirectory`: override the Java source root.
- `spring.correctness.linter.reportDirectory`: change the report output directory.
- `spring.correctness.linter.baselineFile`: change the baseline file location.
- `spring.correctness.linter.writeBaseline=true`: regenerate the baseline file.
- `spring.correctness.linter.failOnSeverity=WARNING`: fail the build for matching severities.
- `spring.correctness.linter.formats=json,html,sarif`: limit generated report formats.
- `spring.correctness.linter.enabledRules=RULE_A,RULE_B`: run only selected rules.
- `spring.correctness.linter.disabledRules=RULE_A,RULE_B`: skip specific rules.
- `spring.correctness.linter.severityOverrides=RULE_A=ERROR,RULE_B=INFO`: override per-rule severities.
- `spring.correctness.linter.useIncrementalCache=true`: reuse cached file analysis when source content is unchanged.
- `spring.correctness.linter.cacheFile=target/spring-correctness-linter/analysis-cache.txt`: customize the incremental cache location.
- `spring.correctness.linter.additionalSourceDirectories=target/generated-sources/foo,src/generated/java`: scan extra source roots in the current module.
- `spring.correctness.linter.scanReactorModules=true`: scan compile source roots from all Maven reactor modules from the execution root.
- `spring.correctness.linter.includeTestSourceRoots=true`: include test compile source roots in the scan.
- `spring.correctness.linter.splitBaselineByModule=true`: write per-module baseline files under `modules/<module>/`.
- `spring.correctness.linter.splitCacheByModule=true`: write per-module analysis cache files under `modules/<module>/`.

On PowerShell, quote dotted `-D` properties or invoke Maven through `cmd /c` to avoid argument parsing issues.

## Inline Suppression

- Preferred prefix: `spring-correctness-linter:disable-next-line RULE_ID reason: explanation`
- Supported scopes: `disable-file`, `disable-line`, `disable-next-line`, `disable-next-method`, `disable-next-type`
- Legacy `medical-linter:` directives are still accepted for backward compatibility.

## Release Notes

- Keep `RELEASE_NOTES_TEMPLATE.md` current for the fallback release body.
- Add versioned notes as `RELEASE_NOTES_vX.Y.Z.md` before cutting a tagged release.
- The release workflow now prefers the matching versioned file and falls back to the template.

## Rule Reference

- Each lint run can generate `rules-reference.md` alongside other reports.
- The generated reference now includes a rule index, default severities, rule-specific disable/enable examples, severity override examples, and suppression snippets.

## Module Summaries

- When scanning multiple source roots or Maven reactor modules, JSON and HTML reports group findings by module.
- Quality gate failures now include the module IDs that triggered the failure threshold.
- Baseline diff output also includes module summaries, and optional per-module baseline/cache files can be generated for reactor-style workflows.

## Samples

- `samples/vulnerable-sample/`: single-module sample for baseline, reports, and common Spring correctness findings.
- `samples/reactor-sample/`: multi-module Maven reactor sample that exercises `scanReactorModules`, module summaries, and per-module baseline/cache output.
