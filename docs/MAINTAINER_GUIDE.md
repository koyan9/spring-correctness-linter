# Maintainer Guide

This guide summarizes the repository maintenance workflow for `spring-correctness-linter`.

## Repository Policy

- `main` is protected on GitHub.
- Normal changes should land through pull requests.
- Required checks on `main`:
  - `Verify (Java 17)`
  - `Verify (Java 21)`
- At least one approving review is required before merge.
- Stale reviews are dismissed after new commits.
- Pull-request conversations must be resolved before merge.

Because this repository is owned by a personal account instead of an organization, GitHub does not support per-user push restrictions. In practice:

- collaborators should use PRs
- the repository owner still has admin-level bypass for emergencies

## Day-to-Day Workflow

Recommended contributor flow:

1. Create a feature branch from `main`.
2. Open a pull request.
3. Wait for `Verify (Java 17)` and `Verify (Java 21)` to pass.
4. Merge only after review approval and resolved conversations.

Recommended maintainer validation before merging high-impact changes:

- `mvnw.cmd -q verify` on Windows, or `./mvnw -q verify` on macOS / Linux
- `mvnw.cmd -q -DskipTests install` on Windows, or `./mvnw -q -DskipTests install` on macOS / Linux
- re-run the relevant sample project when plugin integration or report behavior changes
- run `pwsh -File scripts/benchmark-cache.ps1 -Targets reactor,adoption-all -WarmRuns 1` when cache invalidation, runtime metrics, or source-root behavior changes

## Release Model

Releases are tag-driven and automatic.

Primary path:

1. Prepare the release commit on `main`.
2. Update `CHANGELOG.md` and add `RELEASE_NOTES_vX.Y.Z.md` when needed.
3. Create and push an annotated tag such as `v0.1.2`.
4. GitHub Actions runs the `Release` workflow automatically.
5. The workflow:
   - verifies the project
   - signs artifacts
   - publishes to Maven Central
   - creates the GitHub Release

Manual fallback remains available through `.github/workflows/release.yml` with a `tag` input.

## Release Checklist

Before pushing a release tag, confirm:

- `CHANGELOG.md` reflects the release contents
- `RELEASE_NOTES_vX.Y.Z.md` exists for non-trivial releases
- `README.md` and `README.zh-CN.md` are aligned if user-facing behavior changed
- the annotated release tag you plan to push matches the root `pom.xml` version and root `scm.tag`
- local verification passes
- Central publication credentials and GPG material are still valid

Recommended local checks:

- `mvnw.cmd -q verify`
- `mvnw.cmd -q -Prelease-artifacts clean verify`
- `mvnw.cmd -q -Pcentral-publish -DskipTests verify`
- `mvnw.cmd -q -f samples/vulnerable-sample/pom.xml -DskipTests verify`
- `mvnw.cmd -q -f samples/reactor-sample/pom.xml -DskipTests verify`
- `mvnw.cmd -q -f samples/adoption-suite/pom.xml -DskipTests verify`

## Secrets and Signing

Required GitHub repository secrets:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `MAVEN_GPG_PRIVATE_KEY`
- `MAVEN_GPG_PASSPHRASE`

Local Maven / GPG expectations:

- `~/.m2/settings.xml` should reference environment variables or encrypted Maven credentials
- do not keep plaintext Central tokens in repository files
- do not keep plaintext `gpg.passphrase` in repository files
- the local GPG keyring should contain the release signing key when doing local publication

## Common Maintenance Tasks

Rotate credentials when needed:

- rotate the Sonatype Central token
- update local environment variables
- update GitHub repository secrets
- verify `central-publish` locally before the next release

When changing workflow names or branch policy:

- keep required status-check names stable, or update GitHub branch protection immediately after the rename
- verify that `main` protection still references the correct check names

When changing release automation:

- prefer testing on `main` first
- avoid retagging already-published versions
- if you use the manual workflow fallback, pass the exact release tag that matches the root `pom.xml` version and `scm.tag`
- if a Central publication succeeds but GitHub Release creation fails, do not republish the same version; fix the workflow and create the GitHub Release separately
- if only one of the direct Central artifact URLs is visible, treat it as a partial publication state and inspect before rerunning deploy
- after each release, verify GitHub Actions, the GitHub Release page, and the direct Maven Central artifact URL before treating the release as complete
- use `powershell -ExecutionPolicy Bypass -File scripts\\check-release-status.ps1 -Version X.Y.Z` to capture the post-release status in a repeatable way
- if the release tag exists but GitHub Release or Maven Central is still missing, work through the `If the tagged release fails` section in `docs/RELEASE_PROCESS.md` before retagging or changing versions
- when preparing a version bump, verify that both module `pom.xml` files inherit the same parent version as the root release version

## References

- `docs/RELEASE_PROCESS.md`
- `docs/PERFORMANCE_BENCHMARKING.md`
- `CONTRIBUTING.md`
- `quick-start.md`
- `quick-start.zh-CN.md`
