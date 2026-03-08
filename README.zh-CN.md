# spring-correctness-linter

[English](README.md)

`spring-correctness-linter` 是一个面向 Spring 项目的、以 Maven 为主的静态分析工具。它关注的不是通用 Java 代码风格，而是 **Spring 运行时语义与正确性风险** —— 也就是那些“能够编译通过、但很容易在运行期或集成环境里出问题”的代码模式。

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

- `@Async` 误用
- `@Transactional` 误用
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

## Baseline 与增量缓存

项目支持两类互补机制：

- **Baseline**：接受当前已知问题，后续只关注新增问题
- **Incremental Cache**：基于文件内容哈希复用未变化文件的分析结果，减少重复计算

支持的模式：

- 单一 baseline 文件
- 按模块拆分 baseline
- 单一 cache 文件
- 按模块拆分 cache

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

- `spring.correctness.linter.sourceDirectory`：覆盖主源码目录
- `spring.correctness.linter.additionalSourceDirectories`：补充当前模块额外源码根
- `spring.correctness.linter.scanReactorModules=true`：从 reactor 执行根扫描全部模块
- `spring.correctness.linter.includeTestSourceRoots=true`：把测试源码根也纳入扫描
- `spring.correctness.linter.reportDirectory`：覆盖报告目录
- `spring.correctness.linter.baselineFile`：覆盖 baseline 文件路径
- `spring.correctness.linter.writeBaseline=true`：重新生成 baseline
- `spring.correctness.linter.enabledRules=RULE_A,RULE_B`：只启用指定规则
- `spring.correctness.linter.disabledRules=RULE_A,RULE_B`：禁用指定规则
- `spring.correctness.linter.severityOverrides=RULE_A=ERROR,RULE_B=INFO`：覆盖规则默认严重级别
- `spring.correctness.linter.failOnSeverity=WARNING`：按严重级别失败
- `spring.correctness.linter.failOnError=true`：只要还有可见问题就失败
- `spring.correctness.linter.useIncrementalCache=true`：启用增量缓存
- `spring.correctness.linter.cacheFile=target/spring-correctness-linter/analysis-cache.txt`：设置 cache 文件路径
- `spring.correctness.linter.splitBaselineByModule=true`：按模块拆分 baseline
- `spring.correctness.linter.splitCacheByModule=true`：按模块拆分 cache

PowerShell 下，建议对带点号的 `-Dspring.correctness.linter.*` 参数加引号，或者通过 `cmd /c` 调用 Maven。

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

## 当前项目状态

当前项目已经具备较完整的 Spring correctness linting 工作流，适合用于：

- 本地开发阶段的风险前移
- CI 质量门禁
- 老项目 baseline 治理
- 多模块仓库按模块治理和持续改进
