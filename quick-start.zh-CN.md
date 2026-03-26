# spring-correctness-linter 快速上手

这份文档面向希望把 `spring-correctness-linter` 接入现有 Spring 项目的团队。

重点说明：

- 如何把 Maven 插件加到其他项目
- 第一轮应该怎么跑
- baseline、缓存、质量门禁应该如何逐步启用
- 哪些配置适合真实项目落地
- 如何从本地试跑过渡到 CI

完整参数表请参考 [README.zh-CN.md](README.zh-CN.md)。

## 1. 适用前提

当前项目主要面向：

- Java 17+
- Maven 构建
- 依赖 Spring 注解、代理、事务、调度、事件、缓存和控制器安全语义的项目

建议先选一个普通单模块 Spring 项目做试点。

## 2. 在其他项目中引入

把下面的插件配置加入目标项目的 `pom.xml`：

```xml
<plugin>
  <groupId>io.github.koyan9</groupId>
  <artifactId>spring-correctness-linter-maven-plugin</artifactId>
  <version>0.1.2</version>
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

如果是在本地 checkout 当前仓库做预接入验证，先安装本地产物：

```bash
./mvnw -q -DskipTests install
```

Windows:

```powershell
.\mvnw.cmd -q -DskipTests install
```

## 3. 第一轮运行

第一轮先不要打开严格门禁：

```bash
mvn -q verify
```

重点检查：

- `target/spring-correctness-linter/lint-report.html`
- `target/spring-correctness-linter/lint-report.json`
- `target/spring-correctness-linter/rules-reference.md`
- `target/spring-correctness-linter/rules-governance.json`

看什么：

- 命中了哪些规则
- 是否存在 parse problem
- 报告里的问题是不是确实成立
- 当前启用了哪些规则域与规则 ID

## 4. 不要一开始就全量启用

大多数真实项目建议先从较小范围开始：

```xml
<configuration>
  <enabledRuleDomains>ASYNC,TRANSACTION,WEB</enabledRuleDomains>
</configuration>
```

这组配置适合第一轮试点，因为它覆盖了：

- 代理边界错误
- 事务问题
- 控制器入口安全意图

常见聚焦组合：

- `SCHEDULED`
- `LIFECYCLE`
- `TRANSACTION,EVENTS`
- `CACHE`
- `WEB`

## 5. baseline 渐进接入

如果项目里已经有历史问题，先生成 baseline：

```bash
mvn -q io.github.koyan9:spring-correctness-linter-maven-plugin:0.1.2:lint \
  "-Dspring.correctness.linter.writeBaseline=true"
```

默认会生成：

- `spring-correctness-linter-baseline.txt`

后续常规运行保持：

```xml
<configuration>
  <applyBaseline>true</applyBaseline>
</configuration>
```

建议：

- baseline 文件提交到版本库
- 配合 `baseline-diff.json` / `baseline-diff.html` 做 review
- 不要频繁无目的重刷 baseline

## 6. 增量缓存

本地和 CI 一般都应该打开：

```xml
<configuration>
  <useIncrementalCache>true</useIncrementalCache>
</configuration>
```

默认缓存文件：

- `target/spring-correctness-linter/analysis-cache.txt`

建议：

- CI 中把缓存目录持久化
- 不要把 cache 文件提交到 git

## 7. 打开质量门禁

规则集合稳定以后，再打开门禁：

```xml
<configuration>
  <failOnSeverity>WARNING</failOnSeverity>
</configuration>
```

或者使用更简单的兜底模式：

```xml
<configuration>
  <failOnError>true</failOnError>
</configuration>
```

注意：

- `failOnSeverity` 优先级高于 `failOnError`

## 8. 常见真实项目配置

### 集中式安全

如果安全策略不写在 Controller 上：

```xml
<configuration>
  <assumeCentralizedSecurity>true</assumeCentralizedSecurity>
</configuration>
```

或者开启自动检测：

```xml
<configuration>
  <autoDetectCentralizedSecurity>true</autoDetectCentralizedSecurity>
</configuration>
```

或者登记项目自己的安全注解：

```xml
<configuration>
  <securityAnnotations>InternalEndpoint,TeamSecure</securityAnnotations>
</configuration>
```
<!-- 插件会去掉 `@` 与包名前缀，必须按配置的简单名使用注解。 -->

### Cache 约定

允许特定 cache 使用默认 key：

```xml
<configuration>
  <cacheDefaultKeyCacheNames>users,orders</cacheDefaultKeyCacheNames>
</configuration>
```

如果项目统一约定了全局 key generator：

```xml
<configuration>
  <autoDetectProjectWideKeyGenerator>true</autoDetectProjectWideKeyGenerator>
</configuration>
```

### 轻量 JSON

如果只需要机器可读摘要：

```xml
<configuration>
  <formats>
    <format>json</format>
  </formats>
  <lightweightReports>true</lightweightReports>
</configuration>
```

### 显式控制并发

控制分析线程数：

```xml
<configuration>
  <parallelFileAnalysis>true</parallelFileAnalysis>
  <fileAnalysisParallelism>4</fileAnalysisParallelism>
</configuration>
```

强制串行：

```xml
<configuration>
  <parallelFileAnalysis>false</parallelFileAnalysis>
</configuration>
```

### 多模块 reactor

```xml
<configuration>
  <scanReactorModules>true</scanReactorModules>
  <splitBaselineByModule>true</splitBaselineByModule>
  <splitCacheByModule>true</splitCacheByModule>
</configuration>
```

只有真正贡献 Java 源码的模块才会出现在模块报告和按模块产物中。

如果某些模块有额外源码根：

```xml
<configuration>
  <moduleSourceDirectories>root-app=src/it/java;module-a=src/generated/java</moduleSourceDirectories>
</configuration>
```

## 9. CI 典型命令

```bash
mvn -B -q verify \
  "-Dspring.correctness.linter.applyBaseline=true" \
  "-Dspring.correctness.linter.failOnSeverity=WARNING"
```

## 10. 推荐接入顺序

1. 先只接入报告
2. 本地试跑，不开门禁
3. 如有噪声，先缩小到规则域组合
4. 生成 baseline 并提交
5. 打开增量缓存
6. 打开质量门禁
7. 逐步扩大规则范围
8. 对高信号规则提升严重级别

## 11. 运行后重点看什么

- `lint-report.html`：人工 review
- `lint-report.json`：自动化消费
- `rules-governance.json`：确认当前生效的规则与严重级别
- `baseline-diff.*`：判断新增 / 匹配 / 过期问题

## 12. 常见问题

如果没有输出报告：

- 检查 `formats`
- 检查 `reportDirectory`
- 确认插件 goal 已经绑定或显式执行

如果门禁失败但不清楚原因：

- 检查 `failOnSeverity` / `failOnError`
- 打开 `rules-governance.json`
- 确认 baseline 是否生效

如果 cache 命中率一直是 0：

- 检查 `cacheFile` 路径是否稳定
- 检查 CI 是否恢复了缓存目录
- 规则或配置变化会触发安全失效

如果控制器安全告警过多：

- `assumeCentralizedSecurity=true`
- 或 `autoDetectCentralizedSecurity=true`
- 或登记内部安全注解

如果 cache key 告警过多：

- 按 cache 名称 allowlist
- 如果确有全局 key 约定，再显式开启项目级 key generator 自动检测

## 13. 推荐样例

- `samples/vulnerable-sample/`
- `samples/reactor-sample/`
- `samples/adoption-suite/basic-app/`
- `samples/adoption-suite/centralized-security-app/`
- `samples/adoption-suite/cache-convention-app/`

如果你想验证集中式安全或项目级 cache key 约定这类真实接入路径，优先参考 `samples/adoption-suite/`。

## 14. 相关文档

- [README.md](README.md)
- [README.zh-CN.md](README.zh-CN.md)
- [quick-start.md](quick-start.md)
- [docs/RULE_DEVELOPMENT.md](docs/RULE_DEVELOPMENT.md)
- [docs/ACCURACY_BACKLOG.md](docs/ACCURACY_BACKLOG.md)
- [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md)
