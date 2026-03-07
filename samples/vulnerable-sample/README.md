# vulnerable-sample

这个示例故意包含多条 Spring 设计/正确性问题，用来演示 `spring-correctness-linter` 的输出。

## 使用方式

1. 先在工作区根目录执行：
   - `mvn -s .mvn-settings.xml install -DskipTests`
2. 进入当前目录后执行：
   - `mvn verify`
3. 查看生成报告：
   - `target/medical-linter/lint-report.json`
   - `target/medical-linter/lint-report.html`
   - `target/medical-linter/lint-report.sarif.json`

## baseline 演示

- 生成 baseline：`mvn medical-linter:lint -DwriteBaseline=true`
- 后续再执行 `mvn verify`，只会留下 baseline 之外的新问题。