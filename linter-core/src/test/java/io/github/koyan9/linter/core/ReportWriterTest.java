/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesGeneratedRuleReferenceMarkdown() throws Exception {
        Path output = tempDir.resolve("reports/rules-reference.md");
        List<RuleDescriptor> rules = List.of(
                new RuleDescriptor("RULE_A", "Rule A", "Description A", LintSeverity.WARNING),
                new RuleDescriptor("RULE_B", "Rule B", "Description B", LintSeverity.ERROR)
        );

        new ReportWriter().writeRulesMarkdown(rules, output);
        String markdown = Files.readString(output);

        assertTrue(markdown.contains("# Generated Rule Reference"));
        assertTrue(markdown.contains("## Rule Index"));
        assertTrue(markdown.contains("| `RULE_A` | `WARNING` | Rule A |"));
        assertTrue(markdown.contains("### `RULE_B`"));
        assertTrue(markdown.contains("spring.correctness.linter.severityOverrides=RULE_B=ERROR"));
        assertTrue(markdown.contains("spring-correctness-linter:disable-next-line RULE_A"));
    }

    @Test
    void writesJsonAndHtmlWithParseProblems() throws Exception {
        Path jsonOutput = tempDir.resolve("reports/lint-report.json");
        Path htmlOutput = tempDir.resolve("reports/lint-report.html");
        LintReport report = new LintReport(
                tempDir,
                tempDir.resolve("src/main/java"),
                List.of(tempDir.resolve("src/main/java"), tempDir.resolve("module-a/src/main/java")),
                Instant.now(),
                List.of(),
                List.of(new LintIssue("RULE_A", LintSeverity.WARNING, "Message", tempDir.resolve("src/main/java/demo/Demo.java"), 1)),
                0,
                0,
                0,
                1,
                List.of(
                        new ModuleSummary("root-app", 1, 1, 1, 1, 0),
                        new ModuleSummary("module-a", 1, 0, 0, 0, 1)
                ),
                java.util.Map.of(tempDir.resolve("src/main/java/demo/Demo.java").toAbsolutePath().normalize().toString(), "root-app"),
                List.of(new SourceParseProblem(tempDir.resolve("src/main/java/demo/Broken.java"), List.of("Parse error")))
        );

        ReportWriter writer = new ReportWriter();
        writer.writeJson(report, jsonOutput);
        writer.writeHtml(report, htmlOutput);

        String json = Files.readString(jsonOutput);
        String html = Files.readString(htmlOutput);

        assertTrue(json.contains("\"parseProblemFileCount\": 1"));
        assertTrue(json.contains("\"moduleSummaries\""));
        assertTrue(json.contains("\"moduleId\": \"root-app\""));
        assertTrue(json.contains("\"module\": \"root-app\""));
        assertTrue(json.contains("Broken.java"));
        assertTrue(html.contains("<h2>Modules</h2>"));
        assertTrue(html.contains("root-app"));
        assertTrue(html.contains("Files with parse problems"));
        assertTrue(html.contains("Broken.java"));
    }

    @Test
    void writesBaselineDiffHtmlWithModuleSummaries() throws Exception {
        Path output = tempDir.resolve("reports/baseline-diff.html");
        BaselineDiffReport diffReport = new BaselineDiffReport(
                List.of(new LintIssue("RULE_A", LintSeverity.WARNING, "New issue", tempDir.resolve("src/main/java/demo/Demo.java"), 3)),
                java.util.Set.of(),
                java.util.Set.of(new BaselineEntry("RULE_B", "src/main/java/demo/Old.java", 9, "Stale issue")),
                List.of(new BaselineDiffModuleSummary("root-app", 1, 0, 1)),
                java.util.Map.of(tempDir.resolve("src/main/java/demo/Demo.java").toAbsolutePath().normalize().toString(), "root-app"),
                java.util.Map.of("src/main/java/demo/Old.java", "root-app")
        );

        new ReportWriter().writeBaselineDiffHtml(diffReport, output);
        String html = Files.readString(output);

        assertTrue(html.contains("baseline diff"));
        assertTrue(html.contains("<h2>Modules</h2>"));
        assertTrue(html.contains("root-app"));
        assertTrue(html.contains("New Issues"));
        assertTrue(html.contains("Stale Baseline Entries"));
    }
}
