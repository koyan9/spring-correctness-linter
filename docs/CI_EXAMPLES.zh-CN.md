# CI 示例

这份文档提供可直接复制的 `spring-correctness-linter` CI 示例，覆盖以下常见接入方式：

- 基于严重级别的质量门禁
- baseline 渐进式接入
- SARIF 上传到 GitHub code scanning

## 1. GitHub Actions 质量门禁

当仓库已经提交 baseline，且希望 CI 对新的 `WARNING` 及以上问题失败时，可直接使用：

```yaml
name: Verify

on:
  pull_request:
  push:
    branches: [main]

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Cache linter analysis
        uses: actions/cache@v4
        with:
          path: .cache/spring-correctness-linter
          key: ${{ runner.os }}-linter-${{ hashFiles('**/pom.xml') }}

      - name: Verify with quality gate
        run: >
          mvn -B -q verify
          "-Dspring.correctness.linter.applyBaseline=true"
          "-Dspring.correctness.linter.failOnSeverity=WARNING"
          "-Dspring.correctness.linter.cacheFile=.cache/spring-correctness-linter/analysis-cache.txt"
```

说明：

- `failOnSeverity` 的优先级高于 `failOnError`
- baseline 提交后，CI 应保持 `applyBaseline=true`
- 如果缓存命中异常，优先看 `runtimeMetrics.cacheMissReasons`

## 2. baseline 渐进式接入

适合已有历史问题的项目，先接受旧问题，再逐步收紧质量门禁。

### 一次性生成 baseline

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.3:lint \
  "-Dspring.correctness.linter.writeBaseline=true"
```

Reactor 项目可使用：

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.3:lint \
  "-Dspring.correctness.linter.scanReactorModules=true" \
  "-Dspring.correctness.linter.splitBaselineByModule=true"
```

生成后请把 baseline 文件或模块 baseline 提交到版本库。

### rollout 初期的报告型 CI

```yaml
name: Verify Without Gate

on:
  pull_request:

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Verify with baseline applied
        run: >
          mvn -B -q verify
          "-Dspring.correctness.linter.applyBaseline=true"
```

推荐顺序：

1. 生成并提交 baseline
2. CI 先只启用 `applyBaseline=true`，不要立刻加失败阈值
3. 持续查看 `baseline-diff.json` / `baseline-diff.html`
4. 团队接受当前可见问题后，再开启 `failOnSeverity`

## 3. GitHub Code Scanning 与 SARIF

如果希望结果出现在 GitHub code scanning，可直接使用：

```yaml
name: Code Scanning

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read
  security-events: write

jobs:
  sarif:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Verify with SARIF output
        run: >
          mvn -B -q verify
          "-Dspring.correctness.linter.formats=sarif"

      - name: Upload SARIF
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: target/spring-correctness-linter/lint-report.sarif.json
```

对于从 reactor 执行根扫描的多模块项目：

- 上传 reactor 根报告目录中的 SARIF 文件
- 使用执行根生成的根级 SARIF 文件
- SARIF 中还会带 `moduleId`，方便按模块查看

## 4. 排查清单

如果工作流成功，但缓存效果不符合预期：

- 检查 `lint-report.json` 中的 `runtimeMetrics.cacheMissReasons`
- 用 `scripts/benchmark-cache.ps1` 对比冷启动和热启动
- 确认 cache 路径在 CI 中保持稳定
- 确认 source roots 没有发生意外变化

如果 SARIF 上传成功但 code scanning 里暂时没有结果：

- 确认开启了 `sarif` 输出
- 确认上传路径和生成路径一致
- 确认 workflow 具备 `security-events: write`

## 5. 相关文档

- [quick-start.zh-CN.md](../quick-start.zh-CN.md)
- [README.zh-CN.md](../README.zh-CN.md)
- [docs/PERFORMANCE_BENCHMARKING.md](PERFORMANCE_BENCHMARKING.md)
- [docs/RELEASE_PROCESS.md](RELEASE_PROCESS.md)
