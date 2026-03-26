# spring-correctness-linter Release Notes

## Highlights

- 
- 

## Included Rule Areas

- Added:
- Expanded:
- Removed or deprecated:

## Tooling

- Maven plugin changes:
- Report generation changes:
- Sample project changes:

## Breaking Changes

- None

## Reports and CI

- JSON / HTML report changes:
- SARIF or code scanning changes:
- Baseline / baseline diff changes:
- Multi-module, reactor, or cache behavior changes:
- Performance or cache evidence:

## Upgrade and Adoption Notes

- Recommended Maven configuration changes:
- New or renamed Maven properties:
- Cache invalidation or analysis fingerprint notes:
- PowerShell, sample, or CI workflow notes:

## Verification

- `./mvnw -q verify`
- `./mvnw -q -f samples/vulnerable-sample/pom.xml -DskipTests verify`
- `./mvnw -q -f samples/reactor-sample/pom.xml -DskipTests verify`
- `pwsh -File scripts/benchmark-cache.ps1 -Targets reactor,adoption-all -WarmRuns 1` when cache, runtime, source-root, or report-performance behavior changed
