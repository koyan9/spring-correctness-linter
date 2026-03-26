# Release Process

This document describes the recommended release workflow for `spring-correctness-linter`.

## When to prepare versioned release notes

Add a dedicated `RELEASE_NOTES_vX.Y.Z.md` file when the release includes any of the following:

- new rules or meaningful rule-severity changes
- report format changes, baseline behavior changes, or cache behavior changes
- Maven plugin configuration changes
- sample, CI, or adoption guidance changes that users are likely to notice

Template fallback is acceptable for small internal or prerelease builds when you do not need a curated changelog narrative. In that case, the release workflow falls back to `RELEASE_NOTES_TEMPLATE.md` and auto-fills the release name, tag, and prerelease metadata.

## Recommended pre-release checklist

Run these commands from the repository root before triggering the release workflow:

- `mvnw.cmd -q verify` on Windows, or `./mvnw -q verify` on macOS / Linux
- `mvnw.cmd -q -Prelease-artifacts verify` on Windows, or `./mvnw -q -Prelease-artifacts verify` on macOS / Linux
- `mvnw.cmd -q -DskipTests install` on Windows, or `./mvnw -q -DskipTests install` on macOS / Linux
- `mvnw.cmd -q -f samples/vulnerable-sample/pom.xml -DskipTests verify` on Windows, or `./mvnw -q -f samples/vulnerable-sample/pom.xml -DskipTests verify` on macOS / Linux
- `mvnw.cmd -q -f samples/reactor-sample/pom.xml -DskipTests verify` on Windows, or `./mvnw -q -f samples/reactor-sample/pom.xml -DskipTests verify` on macOS / Linux
- `mvnw.cmd -q -f samples/adoption-suite/pom.xml -DskipTests verify` on Windows, or `./mvnw -q -f samples/adoption-suite/pom.xml -DskipTests verify` on macOS / Linux
- `mvnw.cmd -q -Pcentral-publish -DskipTests verify` on Windows, or `./mvnw -q -Pcentral-publish -DskipTests verify` on macOS / Linux

Also confirm the following before release:

- `CHANGELOG.md` reflects the release contents
- `README.md` and `README.zh-CN.md` are aligned if user-facing behavior changed
- release notes mention any new Maven properties, cache invalidation semantics, or SARIF / CI integration changes
- run `pwsh -File scripts/benchmark-cache.ps1 -Targets reactor,adoption-all -WarmRuns 1` and summarize the results in release notes when cache, runtime, or report-performance behavior changed

## Release workflow triggers

The primary release path is now automatic:

- push an annotated tag such as `v0.1.2`
- GitHub Actions publishes the Maven Central bundle
- GitHub Actions creates the GitHub Release after Central publication succeeds

Manual fallback is still available through `.github/workflows/release.yml` with a single `tag` input when you need to rerun a tagged release job.

The workflow currently:

- runs only for `v*` tags or explicit manual fallback
- checks out the requested Git tag instead of the branch tip
- imports Maven Central credentials and GPG signing material from GitHub Secrets
- runs `mvn -B -q verify`
- checks whether the tagged version is already visible in Maven Central
- runs `mvn -B -q -Pcentral-publish -DskipTests -Dcentral.publish.auto=true -Dcentral.publish.waitUntil=validated deploy`
- skips the deploy step on reruns when Central already contains the release, and instead rebuilds signed local artifacts for the GitHub release step
- collects generated JARs, POMs, and ASCII-armored signatures from `linter-core` and `linter-maven-plugin`
- uses `RELEASE_NOTES_vX.Y.Z.md` when present, otherwise falls back to `RELEASE_NOTES_TEMPLATE.md`
- creates the GitHub release after Central upload and validation succeed, without waiting for the package to finish propagating to every public endpoint

Required GitHub Secrets:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `MAVEN_GPG_PRIVATE_KEY`
- `MAVEN_GPG_PASSPHRASE`

## Current release scope

The current repository is prepared for:

- version bumps in the Maven project metadata
- GitHub release note generation
- GitHub release artifact publication
- local Maven Central publication through the `central-publish` profile
- GitHub Actions based Maven Central publication for tagged releases
- protected-branch contribution flow on `main`

## Protected main branch

Recommended GitHub branch protection for `main`:

- require pull requests before merge
- require one approving review
- dismiss stale reviews when new commits are pushed
- require conversation resolution before merge
- require passing checks:
  - `Verify (Java 17)`
  - `Verify (Java 21)`
- on this personal repository, collaborators must use PRs while the repository owner retains admin-level bypass

This keeps external collaborators on a PR-based workflow while preserving an owner override for emergencies.

## Local Maven Central publication

Current local publication prerequisites:

- `settings.xml` contains a `server` with id `central`
- that `server` uses a Sonatype Central Portal user token, not a normal website password
- GPG signing is available locally
- the namespace for `io.github.koyan9` is already verified in Sonatype Central Portal
- local credentials are provided through environment variables or encrypted Maven settings, not plaintext values committed to the repository

The parent `pom.xml` now provides a `central-publish` profile that:

- attaches sources
- attaches javadocs
- signs artifacts with GPG
- publishes through `org.sonatype.central:central-publishing-maven-plugin`
- defaults to manual Central publication locally and can be overridden in CI with `-Dcentral.publish.auto=true -Dcentral.publish.waitUntil=published`

Recommended secure `settings.xml` pattern:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>${env.MAVEN_CENTRAL_USERNAME}</username>
      <password>${env.MAVEN_CENTRAL_PASSWORD}</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>central</id>
      <properties>
        <gpg.passphrase>${env.MAVEN_GPG_PASSPHRASE}</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>central</activeProfile>
  </activeProfiles>
</settings>
```

If `gpg` is not on `PATH`, define `gpg.executable` in a local-only Maven profile instead of hardcoding it into the repository.

Recommended local publish command:

- Windows:
  - `mvnw.cmd -q -Pcentral-publish -DskipTests deploy`
- macOS / Linux:
  - `./mvnw -q -Pcentral-publish -DskipTests deploy`

If your GPG setup requires loopback passphrase handling, provide the passphrase in the way your local GPG setup expects before running the command.

Recommended dry run before the real publish:

- `mvnw.cmd -q -Pcentral-publish -DskipTests verify` on Windows
- `./mvnw -q -Pcentral-publish -DskipTests verify` on macOS / Linux

This validates sources, javadocs, and signing configuration before a real deploy attempt.

For local deploys, the default `central-publish` profile uploads for manual review only. The GitHub Actions workflow overrides this to automatic publish and waits for the deployment to reach the `published` state before creating the GitHub release.

## Suggested release-note content

For non-trivial releases, prefer filling these sections explicitly:

- `Highlights`
- `Included Rule Areas`
- `Tooling`
- `Reports and CI`
- `Upgrade and Adoption Notes`
- `Verification`

When the release changes cache invalidation, runtime metrics, or multi-module/report performance behavior, also include:

- the benchmark command used
- a short cold vs warm summary
- any notable `cacheMissReasons` or cache-hit changes

Keep the notes focused on externally visible changes rather than internal refactors unless those refactors affect extensibility, performance, or upgrade risk.
