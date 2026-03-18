# spring-correctness-linter

[English](README.md)

`spring-correctness-linter` 是一个面向 Spring 项目的、以 Maven 为主的静态分析工具。它关注的不是通用 Java 代码风格，而是 **Spring 运行时语义与正确性风险** —— 也就是那些“能够编译通过、但很容易在运行期或集成环境里出问题”的代码模式。

## 亮点

- 聚焦 Spring correctness，而不是通用 Java 风格 lint
- 基于 AST 分析代理、事务、异步、事件、缓存和控制器安全意图相关风险
- 支持 baseline，适合遗留项目渐进式治理
- 支持按严重级别在 CI 中做质量门禁，并带模块级失败提示
- 支持多 source root 和 Maven reactor，多模块仓库可直接接入
- 支持 JSON / HTML / SARIF / baseline diff 等多种输出，适配本地、CI 和代码扫描场景

## 背景与目标

Spring 大量依赖注解、代理和运行时语义。很多问题在编译期不会报错，但在实际运行中会出现行为偏差，例如：

- `@Async` 方法无法被正确代理
- `@Transactional` 因 `private`、`final` 或 self-invocation 导致事务失效
- `@EventListener` / `@TransactionalEventListener` 的事务边界不清晰
- Cache 注解组合导致缓存语义模糊或风险增加
- Controller 暴露公共接口却没有显式安全意图
- 条件 Bean 定义之间存在语义冲突

这个项目的目标，就是把这些问题尽量前移到：

- 本地开发阶段
- CI 校验阶段
- 版本发布前的治理阶段

## 预期效果

接入后，团队通常可以获得这些收益：

- 更早发现 Spring 运行时语义问题
- 通过 baseline 接受历史问题，只关注新增风险
- 在 CI 中按严重级别实施质量门禁
- 通过 JSON / HTML / SARIF / baseline diff 等多种报告输出支持不同使用场景
- 在多模块 Maven reactor 项目中按模块聚合结果

## 架构说明

项目主要由两个核心模块组成：

- `linter-core/`：负责 AST 解析、规则执行、报告生成、baseline、增量缓存和模块级聚合
- `linter-maven-plugin/`：负责 Maven 参数解析、源码根收集、调用 core、输出报告和集成质量门禁

配套目录：

- `samples/vulnerable-sample/`：单模块样例，用于验证规则、baseline 和报告输出
- `samples/reactor-sample/`：多模块样例，用于验证 reactor 扫描、模块汇总和模块拆分输出

## 核心执行流程

一次 lint 运行大致分为以下步骤：

1. 收集源码根目录：当前模块、额外 source roots，或整个 Maven reactor
2. 将源码文件加载为 `SourceDocument`，并计算内容哈希
3. 解析为 `SourceUnit`，建立结构缓存，减少重复 AST 遍历
4. 对每个文件执行启用的规则集
5. 应用 inline suppression
6. 应用 baseline 过滤
7. 按严重级别和模块聚合结果
8. 执行质量门禁
9. 输出报告、baseline、cache 等产物

## 当前规则关注范围

默认规则集目前主要覆盖：

- `@Async` 误用（如 private/final 方法、自调用）
- 生命周期与启动边界审查，例如 `@PostConstruct`、`afterPropertiesSet()`、`ApplicationRunner`、`SmartInitializingSingleton` 与代理相关注解组合
- `@Scheduled` 误用与调度边界审查
- `@Transactional` 误用（如 private/final 方法、final 类、自调用、高风险传播）
- `@EventListener` / `@TransactionalEventListener` 边界问题
- Cache key 与 Cache 注解组合风险
- Controller 安全意图检查
- 条件 Bean 冲突检查

## 快速开始

### 运行项目校验

```bash
./mvnw -q verify
```

### 安装本地产物

```bash
./mvnw -q -DskipTests install
```

### 生成 baseline

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0:lint \
  -Dspring.correctness.linter.writeBaseline=true
```

### 按严重级别触发失败

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0:lint \
  -Dspring.correctness.linter.failOnSeverity=WARNING
```

## Maven 使用方式

示例配置：

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

## 主要输出产物

默认输出目录为 `target/spring-correctness-linter/`，常见文件包括：

- `lint-report.json`
- `lint-report.html`
- `lint-report.sarif.json`
- `baseline-diff.json`
- `baseline-diff.html`
- `rules-reference.md`

当启用模块拆分时，还会在 `modules/<module>/` 下写入模块级 baseline / cache 文件。

JSON 和 HTML 报告现在还会包含运行期指标，便于观察：

- 总分析耗时
- 实际分析文件数与缓存命中文件数
- cache 命中率与 cache 作用域
- 各分析阶段耗时拆分
- 各模块分析耗时
- 各模块分析耗时拆分（analyzedMillis/cachedMillis）
- 各模块 cache 命中率
- 最慢的模块（按实际分析耗时 Top N）
- 本次运行配置的启用规则域、禁用规则域，以及最终生效的规则域
- 本次运行配置的启用规则 ID、禁用规则 ID，以及按规则域汇总的最终生效规则清单

## Baseline 与增量缓存

项目支持两类互补机制：

- **Baseline**：接受当前已知问题，后续只关注新增问题
- **Incremental Cache**：基于文件内容哈希复用未变化文件的分析结果，减少重复计算

支持的模式：

- 单一 baseline 文件
- 按模块拆分 baseline
- 单一 cache 文件
- 按模块拆分 cache

当有效分析指纹发生变化时，增量缓存会自动失效并重新分析。当前分析指纹会覆盖以下因素：

- 实际启用的规则集合
- 规则严重级别覆盖配置
- inline suppression 行为
- 当前分析引擎实现

这样既能保证缓存复用的性能收益，也能避免在分析配置已发生实质变化时错误复用旧结果。

## 多模块与 Reactor 支持

当前支持：

- 单模块单源码根
- 单模块多源码根
- Maven reactor 多模块扫描

启用 reactor 扫描后：

- 可以从执行根扫描所有 reactor 模块的 compile source roots
- 报告按模块聚合输出
- 质量门禁失败信息会带上出问题的模块名
- baseline / cache 可以按模块拆分输出

## 关键配置项

列表型配置支持逗号或分号分隔，自动去掉空白。
规则 ID 会统一转成大写；规则域大小写不敏感，允许连字符或空格（如 `transaction` / `Transaction` / `TRANSACTION`）。

| 参数 | 默认值 | 取值范围 | 作用 |
| --- | --- | --- | --- |
| `spring.correctness.linter.sourceDirectory` | `${project.basedir}/src/main/java` | 路径（绝对或相对） | 覆盖主源码目录。相对路径以项目根目录为基准。 |
| `spring.correctness.linter.additionalSourceDirectories` | 空 | 以 `,` 或 `;` 分隔的路径 | 补充当前模块的额外源码根目录。相对路径以项目根目录为基准。 |
| `spring.correctness.linter.moduleSourceDirectories` | 空 | `moduleId=path1,path2;moduleId2=path3` | 为指定模块补充额外源码根目录，路径相对模块根目录解析。未知模块会导致构建失败。 |
| `spring.correctness.linter.scanReactorModules` | `false` | `true` / `false` | 从执行根扫描整个 Maven reactor；开启后非执行根模块会跳过。 |
| `spring.correctness.linter.includeTestSourceRoots` | `false` | `true` / `false` | 将测试源码根目录也纳入扫描。 |
| `spring.correctness.linter.reportDirectory` | `${project.build.directory}/spring-correctness-linter` | 路径 | 报告输出目录。 |
| `spring.correctness.linter.formats` | `json,html,sarif` | `json`、`html`、`sarif` | 控制核心报告格式，大小写不敏感，未知值会给出警告并忽略。baseline diff 与规则文档由独立开关控制。 |
| `spring.correctness.linter.baselineFile` | `${project.basedir}/spring-correctness-linter-baseline.txt` | 路径 | baseline 文件路径，用于过滤和/或写入。 |
| `spring.correctness.linter.honorInlineSuppressions` | `true` | `true` / `false` | 是否启用 inline suppression 注释。 |
| `spring.correctness.linter.applyBaseline` | `true` | `true` / `false` | 是否应用 baseline 过滤隐藏已知问题。 |
| `spring.correctness.linter.writeBaseline` | `false` | `true` / `false` | 写入新的 baseline（或按模块拆分时写入模块 baseline）。 |
| `spring.correctness.linter.writeBaselineDiff` | `true` | `true` / `false` | baseline 路径存在时写出 `baseline-diff.json` 与 `baseline-diff.html`。 |
| `spring.correctness.linter.writeRuleDocs` | `true` | `true` / `false` | 写出规则参考文档。 |
| `spring.correctness.linter.ruleDocsFileName` | `rules-reference.md` | 文件名或相对路径 | 规则文档文件名（或相对路径），写入到报告目录下。 |
| `spring.correctness.linter.lightweightReports` | `false` | `true` / `false` | 生成精简版 `lint-report.json`（仅摘要与规则选择），以减少报告体积。HTML/SARIF 不受影响。 |
| `spring.correctness.linter.failOnSeverity` | 未设置 | `INFO` / `WARNING` / `ERROR` | 当可见问题达到或超过该阈值时失败。优先级高于 `failOnError`。 |
| `spring.correctness.linter.failOnError` | `false` | `true` / `false` | 当存在可见问题时失败，仅在未设置 `failOnSeverity` 时生效。 |
| `spring.correctness.linter.enabledRules` | 空 | 规则 ID | 仅启用指定规则。未知规则会导致构建失败。 |
| `spring.correctness.linter.disabledRules` | 空 | 规则 ID | 禁用指定规则。未知规则会导致构建失败。 |
| `spring.correctness.linter.enabledRuleDomains` | 空 | `ASYNC`、`LIFECYCLE`、`SCHEDULED`、`CACHE`、`WEB`、`TRANSACTION`、`EVENTS`、`CONFIGURATION`、`GENERAL` | 仅启用指定规则域。未知规则域会导致构建失败。 |
| `spring.correctness.linter.disabledRuleDomains` | 空 | 同上 | 禁用指定规则域。 |
| `spring.correctness.linter.severityOverrides` | 空 | `RULE_ID=INFO|WARNING|ERROR` | 覆盖规则默认严重级别。未知规则会导致构建失败。 |
| `spring.correctness.linter.assumeCentralizedSecurity` | `false` | `true` / `false` | 安全策略集中配置时跳过 `SPRING_ENDPOINT_SECURITY`。 |
| `spring.correctness.linter.autoDetectCentralizedSecurity` | `false` | `true` / `false` | 当检测到 `SecurityFilterChain` 或 `SecurityWebFilterChain` Bean 时自动跳过 `SPRING_ENDPOINT_SECURITY`。 |
| `spring.correctness.linter.securityAnnotations` | 空 | 注解名 | 额外安全注解视为显式安全意图，支持简单名或全限定名（允许前缀 `@`）。 |
| `spring.correctness.linter.cacheDefaultKeyCacheNames` | 空 | cache 名称或 `*` | 指定 cache 名称允许默认 key；`*` 表示全部允许。 |
| `spring.correctness.linter.cacheFile` | `${project.build.directory}/spring-correctness-linter/analysis-cache.txt` | 路径 | 增量缓存文件路径（缓存关闭或按模块拆分时忽略）。 |
| `spring.correctness.linter.useIncrementalCache` | `true` | `true` / `false` | 是否启用增量缓存复用。 |
| `spring.correctness.linter.parallelFileAnalysis` | `true` | `true` / `false` | 当存在多个源码文件时，是否启用按文件并行分析。 |
| `spring.correctness.linter.fileAnalysisParallelism` | `0` | 大于等于 `0` 的整数 | 控制文件分析线程数上限。`0` 表示自动按 CPU 核数决定。 |
| `spring.correctness.linter.splitBaselineByModule` | `false` | `true` / `false` | 按模块拆分 baseline，输出到 `modules/<module>/` 下。 |
| `spring.correctness.linter.splitCacheByModule` | `false` | `true` / `false` | 按模块拆分缓存文件，输出到 `modules/<module>/` 下。 |

### 集中式安全意图

如果安全策略主要在 `SecurityFilterChain`、`SecurityWebFilterChain`、网关或其他基础设施中统一控制，可以通过配置减少噪声，或补充项目内的安全注解：

```xml
<configuration>
  <assumeCentralizedSecurity>true</assumeCentralizedSecurity>
  <!-- 也可以自动检测 SecurityFilterChain / SecurityWebFilterChain -->
  <!-- <autoDetectCentralizedSecurity>true</autoDetectCentralizedSecurity> -->
  <securityAnnotations>InternalEndpoint,TeamSecure</securityAnnotations>
  <!-- 也支持 @InternalEndpoint 或 com.example.InternalEndpoint -->
</configuration>
```

### 允许部分 cache 使用默认 key

如果部分 cache 明确依赖 Spring 默认 key 规则，可以按 cache 名称放行：

```xml
<configuration>
  <cacheDefaultKeyCacheNames>users,orders</cacheDefaultKeyCacheNames>
  <!-- 使用 '*' 允许所有 cache 使用默认 key -->
</configuration>
```

PowerShell 下，建议对带点号的 `-Dspring.correctness.linter.*` 参数加引号，或者通过 `cmd /c` 调用 Maven。

当前内建规则域包括 `ASYNC`、`LIFECYCLE`、`SCHEDULED`、`CACHE`、`WEB`、`TRANSACTION`、`EVENTS`、`CONFIGURATION` 和 `GENERAL`。

推荐的起步组合：

- `CI Starter`：`ASYNC,TRANSACTION,WEB`
- `Lifecycle Focus`：`LIFECYCLE`
- `Scheduled Focus`：`SCHEDULED`
- `Transaction Focus`：`TRANSACTION,EVENTS`
- `Web/API Focus`：`WEB`
- `Cache Focus`：`CACHE`

生成出来的 `rules-reference.md` 会把这些组合展开成具体规则 ID，适合先按规则域接入，再逐步细化。

## 推荐配置模板

### 最小本地接入

```xml
<configuration>
  <formats>
    <format>json</format>
    <format>html</format>
  </formats>
</configuration>
```

### CI 质量门禁

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

### 推荐起步组合

如果你不想一开始就启用完整默认规则集，可以先从这些较小的规则域组合开始。

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

### 遗留项目 baseline 渐进接入

```xml
<configuration>
  <writeBaseline>false</writeBaseline>
  <applyBaseline>true</applyBaseline>
  <useIncrementalCache>true</useIncrementalCache>
</configuration>
```

首次生成 baseline 可执行：

```bash
./mvnw io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.0:lint \
  "-Dspring.correctness.linter.writeBaseline=true"
```

### 多模块 reactor 治理

```xml
<configuration>
  <scanReactorModules>true</scanReactorModules>
  <splitBaselineByModule>true</splitBaselineByModule>
  <splitCacheByModule>true</splitCacheByModule>
  <useIncrementalCache>true</useIncrementalCache>
</configuration>
```

## 落地指南

### 典型落地流程

1. 本地先从 `ASYNC,TRANSACTION,WEB` 等小范围开始，`failOnSeverity` 先不设置。
2. 一次性生成 baseline 并提交到版本库。
3. CI 中开启 `applyBaseline=true` 与 `useIncrementalCache=true`，只关注新增问题。
4. 噪声可控后再逐步扩大规则域或启用全量默认规则。
5. 对高风险规则用 `severityOverrides` 提升到 `ERROR`，逐步收紧门禁。

### CI/CD 配置

最小 Maven CLI 示例：

```bash
mvn -B -q verify \
  "-Dspring.correctness.linter.applyBaseline=true" \
  "-Dspring.correctness.linter.failOnSeverity=WARNING"
```

GitHub Actions 示例（复用缓存）：

```yaml
- name: Cache linter analysis
  uses: actions/cache@v4
  with:
    path: .cache/spring-correctness-linter
    key: ${{ runner.os }}-linter-${{ hashFiles('**/pom.xml') }}

- name: Verify project
  run: mvn -B -q verify "-Dspring.correctness.linter.cacheFile=.cache/spring-correctness-linter/analysis-cache.txt"
```

GitLab CI 示例：

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

### baseline 策略

1. 首次生成 baseline：
   `./mvnw spring-correctness-linter:lint "-Dspring.correctness.linter.writeBaseline=true"`
2. 提交 baseline 文件（或按模块拆分后的 baseline）。
3. CI 中保持 `applyBaseline=true`，只看新增问题。
4. 定期刷新 baseline 并检查 `baseline-diff.*`，确保已知问题逐步减少。
5. reactor 项目优先使用 `splitBaselineByModule=true`，让每个模块独立管理 baseline。

### 增量缓存最佳实践

- 保持 `useIncrementalCache=true`，本地与 CI 都能获得更快的增量扫描。
- `cacheFile` 放到稳定缓存目录并在 CI 中持久化，不要提交到 git。
- reactor 扫描建议使用 `splitCacheByModule=true`，减少跨模块抖动。
- 规则配置或实现变化会自动失效缓存，可放心长期开启。

### 规则治理建议

- 先按规则域逐步启用，稳定后再扩大范围。
- 对高风险规则用 `severityOverrides` 提升到 `ERROR`。
- 把 `disabledRules` / `disabledRuleDomains` 视为待治理清单，定期回收。
- 优先使用带理由的 inline suppression，而不是长期关闭规则域。
- 版本升级后查看 `rules-reference.md`，评估新增规则并更新治理策略。

## 参数示例

仅输出 JSON，自定义报告目录，不生成规则文档：

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

只跑少量规则并覆盖严重级别：

```xml
<configuration>
  <enabledRules>SPRING_ASYNC_VOID,SPRING_TX_SELF_INVOCATION</enabledRules>
  <severityOverrides>SPRING_ASYNC_VOID=ERROR</severityOverrides>
  <failOnSeverity>WARNING</failOnSeverity>
</configuration>
```

启用规则域组合并禁用一个噪声规则：

```xml
<configuration>
  <enabledRuleDomains>ASYNC,TRANSACTION,WEB</enabledRuleDomains>
  <disabledRules>SPRING_ENDPOINT_SECURITY</disabledRules>
</configuration>
```

Reactor 扫描并按模块拆分 baseline/cache：

```xml
<configuration>
  <scanReactorModules>true</scanReactorModules>
  <splitBaselineByModule>true</splitBaselineByModule>
  <splitCacheByModule>true</splitCacheByModule>
  <useIncrementalCache>true</useIncrementalCache>
</configuration>
```

显式控制文件分析并发度：

```xml
<configuration>
  <parallelFileAnalysis>true</parallelFileAnalysis>
  <fileAnalysisParallelism>4</fileAnalysisParallelism>
</configuration>
```

为指定模块补充额外源码根目录：

```xml
<configuration>
  <moduleSourceDirectories>root-app=src/it/java;module-a=src/generated/java</moduleSourceDirectories>
</configuration>
```

允许指定 cache 使用默认 key：

```xml
<configuration>
  <cacheDefaultKeyCacheNames>users,orders</cacheDefaultKeyCacheNames>
</configuration>
```

把自定义安全注解视作显式安全意图：

```xml
<configuration>
  <securityAnnotations>InternalEndpoint,com.example.TeamSecure</securityAnnotations>
</configuration>
```

## 常见问题 FAQ

Q: 为什么会报 “Unknown rule id(s)”？
A: 规则 ID 会统一转成大写，并且会校验有效性。请确认 ID 存在于 `rules-reference.md` 中。

Q: 设置了 `failOnError=true` 但仍然没有失败？
A: 如果设置了 `failOnSeverity`，它会优先生效。需要移除或降低阈值。

Q: baseline 没有生效，问题仍然出现？
A: 确认 `applyBaseline=true` 且 baseline 路径与生成路径一致。

Q: 缓存命中率一直是 0%？
A: 确保 `useIncrementalCache=true`，并让 `cacheFile` 指向 CI 可持久化目录。

Q: 有些问题没有被检测出来？
A: 解析错误会导致部分分析跳过。请查看报告中的 parse problem 区域。

## 报告解读

默认输出目录：`target/spring-correctness-linter/`

- `lint-report.json` / `lint-report.html`：问题详情、严重级别、模块聚合、运行指标。
- `lint-report.sarif.json`：用于 code scanning 的 SARIF 输出。
- `baseline-diff.json` / `baseline-diff.html`：新问题、baseline 匹配、过期条目。
- `rules-reference.md`：规则说明与修复建议。
- `rules-governance.json`：规则启用情况、规则域与严重级别的治理快照。

关键运行指标：

- `totalElapsedMillis`、`sourceFileCount`、`analyzedFileCount`、`cachedFileCount`
- 缓存作用域与命中率
- 多模块场景下的各模块分析耗时与缓存命中率

## 规则治理模板

可以用下面的轻量表格追踪规则治理决策：

| 规则 ID | 规则域 | 严重级别 | 状态 | 理由 | 负责人 | 复审日期 |
| --- | --- | --- | --- | --- | --- | --- |
| SPRING_TX_SELF_INVOCATION | TRANSACTION | WARNING | 启用 | 高风险代理绕过 | 平台组 | 2026-06-30 |

## CI 缓存策略

GitHub Actions（带 restore keys）：

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

GitLab CI（按分支缓存）：

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

推荐写法：

```java
// spring-correctness-linter:disable-next-line RULE_ID reason: explanation
```

支持作用域：

- `disable-file`
- `disable-line`
- `disable-next-line`
- `disable-next-method`
- `disable-next-type`

历史前缀 `medical-linter:` 仍兼容。

## 报告与质量门禁

当前报告已经支持：

- 可见问题输出
- parse problem 可见化
- 缓存命中统计
- 模块级汇总
- baseline diff 汇总

当前质量门禁支持：

- 按严重级别失败
- 失败信息包含触发问题的模块名

当 `failOnSeverity` 被配置时，它会优先生效；`failOnError=true` 更适合作为“只要还有可见问题就失败”的简单兜底模式。

## GitHub Code Scanning 集成

启用 SARIF 输出后，可以在 GitHub Actions 中上传生成的报告到 code scanning：

```yaml
- name: Verify project
  run: mvn -B -q verify

- name: Upload SARIF
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: target/spring-correctness-linter/lint-report.sarif.json
```

如果是从 reactor 执行根扫描多模块项目，请上传 reactor 根报告目录中的 SARIF 文件。
SARIF 结果中还包含 `moduleId` 字段，方便按模块定位问题。

## 验证与覆盖率

推荐验证流程：

1. 运行 `./mvnw -q verify`
2. 根据改动范围重跑样例：
   - `samples/vulnerable-sample/`：单模块、报告、baseline、suppressions 相关
   - `samples/reactor-sample/`：模块扫描、reactor、模块拆分 baseline/cache 相关
3. 对发布前或样例安装链路有影响时，再运行 `./mvnw -q -DskipTests install`

覆盖率：

- `./mvnw verify` 会生成各模块 JaCoCo 报告：`target/site/jacoco/`
- 同时也会生成聚合 JaCoCo 报告：`target/site/jacoco-aggregate/`
- `linter-core` 当前要求最低 `85%` 行覆盖率
- `linter-maven-plugin` 当前要求最低 `75%` 行覆盖率

## 样例工程

### `samples/vulnerable-sample/`

单模块样例，故意保留若干风险 Spring 模式，用于观察规则、baseline 和报告产物。

### `samples/reactor-sample/`

多模块 Maven reactor 样例，用于验证：

- reactor 扫描
- 模块汇总
- 按模块拆分 baseline
- 按模块拆分增量缓存

## 仓库结构

- `linter-core/`：核心分析引擎与规则实现
- `linter-maven-plugin/`：Maven 插件集成层
- `samples/vulnerable-sample/`：单模块样例
- `samples/reactor-sample/`：多模块样例
- `CHANGELOG.md`：变更记录
- `RELEASE_NOTES_TEMPLATE.md`：发布说明模板
- `docs/RELEASE_PROCESS.md`：发布检查清单与工作流说明
- `docs/RULE_DEVELOPMENT.md`：规则实现与语义层开发说明

## 当前项目状态

当前项目已经具备较完整的 Spring correctness linting 工作流，适合用于：

- 本地开发阶段的风险前移
- CI 质量门禁
- 老项目 baseline 治理
- 多模块仓库按模块治理和持续改进

## 里程碑与路线图

- 历史里程碑可参考 `docs/MILESTONES.md`
- 当前 1–2 个迭代的方向可参考 `docs/ROADMAP.md`
