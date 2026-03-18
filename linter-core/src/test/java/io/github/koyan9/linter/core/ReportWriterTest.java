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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesGeneratedRuleReferenceMarkdown() throws Exception {
        Path output = tempDir.resolve("reports/rules-reference.md");
        List<RuleDescriptor> rules = List.of(
                new RuleDescriptor(
                        "RULE_A",
                        "Rule A",
                        "Description A",
                        RuleDomain.WEB,
                        LintSeverity.WARNING,
                        List.of("Applies in controller entrypoints."),
                        List.of("May be acceptable in tightly scoped legacy flows."),
                        List.of("Split the boundary into a separate service.")
                ),
                new RuleDescriptor("RULE_B", "Rule B", "Description B", RuleDomain.TRANSACTION, LintSeverity.ERROR),
                new RuleDescriptor("RULE_C", "Rule C", "Description C", RuleDomain.EVENTS, LintSeverity.WARNING),
                new RuleDescriptor("RULE_D", "Rule D", "Description D", RuleDomain.ASYNC, LintSeverity.WARNING)
        );

        new ReportWriter().writeRulesMarkdown(rules, output);
        String markdown = Files.readString(output);

        assertTrue(markdown.contains("# Generated Rule Reference"));
        assertTrue(markdown.contains("## Recommended Bundles"));
        assertTrue(markdown.contains("### `CI Starter`"));
        assertTrue(markdown.contains("enabledRuleDomains=ASYNC,TRANSACTION,WEB"));
        assertTrue(markdown.contains("### `Transaction Focus`"));
        assertTrue(markdown.contains("enabledRuleDomains=TRANSACTION,EVENTS"));
        assertTrue(markdown.contains("### `Web/API Focus`"));
        assertTrue(markdown.contains("## Rule Domains"));
        assertTrue(markdown.contains("### Web"));
        assertTrue(markdown.contains("## Rule Index"));
        assertTrue(markdown.contains("| `RULE_A` | `WARNING` | Rule A |"));
        assertTrue(markdown.contains("### `RULE_B`"));
        assertTrue(markdown.contains("- Domain: `WEB`"));
        assertTrue(markdown.contains("spring.correctness.linter.severityOverrides=RULE_B=ERROR"));
        assertTrue(markdown.contains("spring-correctness-linter:disable-next-line RULE_A"));
        assertTrue(markdown.contains("#### Applies when"));
        assertTrue(markdown.contains("Applies in controller entrypoints."));
        assertTrue(markdown.contains("#### Common false-positive boundaries"));
        assertTrue(markdown.contains("May be acceptable in tightly scoped legacy flows."));
        assertTrue(markdown.contains("#### Recommended fixes"));
        assertTrue(markdown.contains("Split the boundary into a separate service."));
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
                List.of(new RuleDescriptor(
                        "RULE_A",
                        "Rule A",
                        "Description A",
                        RuleDomain.WEB,
                        LintSeverity.WARNING,
                        List.of("Applies when a public endpoint is exposed."),
                        List.of("Central gateway policy may make this acceptable."),
                        List.of("Add explicit security intent.")
                )),
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
                List.of(new SourceParseProblem(tempDir.resolve("src/main/java/demo/Broken.java"), List.of("Parse error"))),
                new AnalysisRuntimeMetrics(
                        true,
                        "shared-file",
                        "fingerprint-123",
                        42,
                        2,
                        1,
                        1,
                        1,
                        new AnalysisPhaseMetrics(1, 2, 3, 4, 5, 6, 7),
                        List.of(
                                new ModuleRuntimeMetrics("root-app", 1, 1, 0, 1, 11, 11, 0),
                                new ModuleRuntimeMetrics("module-a", 1, 0, 1, 0, 9, 0, 9)
                        )
                ),
                new RuleDomainSelectionSummary(
                        List.of(RuleDomain.WEB),
                        List.of(RuleDomain.CACHE),
                        List.of(RuleDomain.WEB),
                        List.of("RULE_A"),
                        List.of("RULE_IGNORED"),
                        List.of("RULE_A"),
                        List.of(new RuleDomainRuleSummary(RuleDomain.WEB, List.of("RULE_A")))
                )
        );

        ReportWriter writer = new ReportWriter();
        writer.writeJson(report, jsonOutput);
        writer.writeHtml(report, htmlOutput);

        String json = Files.readString(jsonOutput);
        String html = Files.readString(htmlOutput);

        assertTrue(json.contains("\"parseProblemFileCount\": 1"));
        assertTrue(json.contains("\"runtimeMetrics\""));
        assertTrue(json.contains("\"analysisFingerprint\": \"fingerprint-123\""));
        assertTrue(json.contains("\"cacheHitRatePercent\": 50"));
        assertTrue(json.contains("\"slowModules\""));
        assertTrue(json.contains("\"cacheHitRatePercent\": 0"));
        assertTrue(json.contains("\"ruleDomainSelection\""));
        assertTrue(json.contains("\"enabledDomains\""));
        assertTrue(json.contains("\"WEB\""));
        assertTrue(json.contains("\"disabledDomains\""));
        assertTrue(json.contains("\"CACHE\""));
        assertTrue(json.contains("\"enabledRuleIds\""));
        assertTrue(json.contains("\"RULE_IGNORED\""));
        assertTrue(json.contains("\"effectiveRuleBreakdown\""));
        assertTrue(json.contains("\"ruleCount\": 1"));
        assertTrue(json.contains("\"domain\": \"WEB\""));
        assertTrue(json.contains("\"appliesWhen\""));
        assertTrue(json.contains("Applies when a public endpoint is exposed."));
        assertTrue(json.contains("\"commonFalsePositiveBoundaries\""));
        assertTrue(json.contains("Central gateway policy may make this acceptable."));
        assertTrue(json.contains("\"recommendedFixes\""));
        assertTrue(json.contains("Add explicit security intent."));
        assertTrue(json.contains("\"ruleGuidance\""));
        assertTrue(json.contains("\"findingCount\": 1"));
        assertTrue(json.contains("\"staleEntryCount\": 0"));
        assertTrue(json.contains("\"moduleSummaries\""));
        assertTrue(json.contains("\"moduleId\": \"root-app\""));
        assertTrue(json.contains("\"module\": \"root-app\""));
        assertTrue(json.contains("Broken.java"));
        assertTrue(html.contains("<h2>Modules</h2>"));
        assertTrue(html.contains("<h2>Runtime Metrics</h2>"));
        assertTrue(html.contains("Slowest Modules"));
        assertTrue(html.contains("<h2>Rule Guidance</h2>"));
        assertTrue(html.contains("Enabled rule domains"));
        assertTrue(html.contains("Disabled rule domains"));
        assertTrue(html.contains("Effective rule domains"));
        assertTrue(html.contains("Enabled rule ids"));
        assertTrue(html.contains("Disabled rule ids"));
        assertTrue(html.contains("Effective rule ids"));
        assertTrue(html.contains("Effective Rules By Domain"));
        assertTrue(html.contains("href=\"#rule-rule-a\""));
        assertTrue(html.contains("Current visible findings"));
        assertTrue(html.contains("Add explicit security intent."));
        assertTrue(html.contains("fingerprint-123"));
        assertTrue(html.contains("Cache hit rate"));
        assertTrue(html.contains("root-app"));
        assertTrue(html.contains("Files with parse problems"));
        assertTrue(html.contains("Broken.java"));
    }

    @Test
    void writesLightweightJsonWithoutFindingsAndRuntimeSections() throws Exception {
        Path jsonOutput = tempDir.resolve("reports/lint-report-light.json");
        LintReport report = new LintReport(
                tempDir,
                tempDir.resolve("src/main/java"),
                List.of(tempDir.resolve("src/main/java")),
                Instant.now(),
                List.of(new RuleDescriptor("RULE_A", "Rule A", "Description A", RuleDomain.WEB, LintSeverity.WARNING)),
                List.of(new LintIssue("RULE_A", LintSeverity.WARNING, "Message", tempDir.resolve("src/main/java/demo/Demo.java"), 1)),
                1,
                2,
                3,
                4,
                List.of(new ModuleSummary("root-app", 1, 1, 1, 0, 0)),
                java.util.Map.of(tempDir.resolve("src/main/java/demo/Demo.java").toAbsolutePath().normalize().toString(), "root-app"),
                List.of(new SourceParseProblem(tempDir.resolve("src/main/java/demo/Broken.java"), List.of("Parse error"))),
                new AnalysisRuntimeMetrics(
                        true,
                        "shared-file",
                        "fingerprint-123",
                        42,
                        2,
                        1,
                        1,
                        1,
                        new AnalysisPhaseMetrics(1, 2, 3, 4, 5, 6, 7),
                        List.of(new ModuleRuntimeMetrics("root-app", 1, 1, 0, 0, 11, 11, 0))
                ),
                new RuleDomainSelectionSummary(
                        List.of(RuleDomain.WEB),
                        List.of(RuleDomain.CACHE),
                        List.of(RuleDomain.WEB),
                        List.of("RULE_A"),
                        List.of("RULE_IGNORED"),
                        List.of("RULE_A"),
                        List.of(new RuleDomainRuleSummary(RuleDomain.WEB, List.of("RULE_A")))
                )
        );

        new ReportWriter().writeJson(report, jsonOutput, ReportWriter.ReportDetail.LIGHT);
        String json = Files.readString(jsonOutput);

        assertTrue(json.contains("\"summary\""));
        assertTrue(json.contains("\"ruleDomainSelection\""));
        assertTrue(json.contains("\"issueCount\": 1"));
        assertTrue(json.contains("\"cachedFileCount\": 4"));
        assertTrue(json.contains("\"RULE_IGNORED\""));
        assertFalse(json.contains("\"runtimeMetrics\""));
        assertFalse(json.contains("\"issues\""));
        assertFalse(json.contains("\"parseProblems\""));
        assertFalse(json.contains("\"moduleSummaries\""));
        assertFalse(json.contains("\"ruleGuidance\""));
    }

    @Test
    void writesRuleGovernanceSnapshot() throws Exception {
        Path output = tempDir.resolve("reports/rules-governance.json");
        LintReport report = new LintReport(
                tempDir,
                tempDir.resolve("src/main/java"),
                List.of(tempDir.resolve("src/main/java")),
                Instant.now(),
                List.of(
                        new RuleDescriptor("RULE_A", "Rule A", "Description A", RuleDomain.WEB, LintSeverity.WARNING),
                        new RuleDescriptor("RULE_B", "Rule B", "Description B", RuleDomain.TRANSACTION, LintSeverity.ERROR)
                ),
                List.of(),
                0,
                0,
                0,
                0,
                List.of(),
                java.util.Map.of(),
                List.of(),
                AnalysisRuntimeMetrics.empty(),
                new RuleDomainSelectionSummary(
                        List.of(RuleDomain.WEB),
                        List.of(RuleDomain.CACHE),
                        List.of(RuleDomain.WEB, RuleDomain.TRANSACTION),
                        List.of("RULE_A"),
                        List.of("RULE_C"),
                        List.of("RULE_A", "RULE_B"),
                        List.of(
                                new RuleDomainRuleSummary(RuleDomain.WEB, List.of("RULE_A")),
                                new RuleDomainRuleSummary(RuleDomain.TRANSACTION, List.of("RULE_B"))
                        )
                )
        );

        new ReportWriter().writeRuleGovernance(report, output);
        String json = Files.readString(output);

        assertTrue(json.contains("\"selection\""));
        assertTrue(json.contains("\"enabledDomains\": [\"WEB\"]"));
        assertTrue(json.contains("\"disabledDomains\": [\"CACHE\"]"));
        assertTrue(json.contains("\"effectiveRuleIds\": [\"RULE_A\", \"RULE_B\"]"));
        assertTrue(json.contains("\"ruleCount\": 2"));
        assertTrue(json.contains("\"ruleId\": \"RULE_A\""));
        assertTrue(json.contains("\"domain\": \"TRANSACTION\""));
        assertTrue(json.contains("\"severity\": \"ERROR\""));
    }

    @Test
    void writesBaselineDiffHtmlWithModuleSummaries() throws Exception {
        Path jsonOutput = tempDir.resolve("reports/baseline-diff.json");
        Path output = tempDir.resolve("reports/baseline-diff.html");
        BaselineDiffReport diffReport = new BaselineDiffReport(
                List.of(new LintIssue("RULE_A", LintSeverity.WARNING, "New issue", tempDir.resolve("src/main/java/demo/Demo.java"), 3)),
                java.util.Set.of(),
                java.util.Set.of(new BaselineEntry("RULE_B", "src/main/java/demo/Old.java", 9, "Stale issue")),
                List.of(new BaselineDiffModuleSummary("root-app", 1, 0, 1)),
                java.util.Map.of(tempDir.resolve("src/main/java/demo/Demo.java").toAbsolutePath().normalize().toString(), "root-app"),
                java.util.Map.of("src/main/java/demo/Old.java", "root-app"),
                List.of(
                        new RuleDescriptor(
                                "RULE_A",
                                "Rule A",
                                "Description A",
                                RuleDomain.GENERAL,
                                LintSeverity.WARNING,
                                List.of("Applies when a new issue is introduced."),
                                List.of("Legacy baseline entries may still be acceptable temporarily."),
                                List.of("Refactor the affected code path.")
                        ),
                        new RuleDescriptor(
                                "RULE_B",
                                "Rule B",
                                "Description B",
                                RuleDomain.GENERAL,
                                LintSeverity.ERROR,
                                List.of("Applies when stale entries should be reviewed."),
                                List.of(),
                                List.of("Remove stale baseline records once validated.")
                        )
                ),
                new RuleDomainSelectionSummary(
                        List.of(RuleDomain.WEB),
                        List.of(RuleDomain.EVENTS),
                        List.of(RuleDomain.GENERAL),
                        List.of("RULE_A"),
                        List.of("RULE_B"),
                        List.of("RULE_A", "RULE_B"),
                        List.of(new RuleDomainRuleSummary(RuleDomain.GENERAL, List.of("RULE_A", "RULE_B")))
                )
        );

        new ReportWriter().writeBaselineDiff(diffReport, jsonOutput);
        new ReportWriter().writeBaselineDiffHtml(diffReport, output);
        String json = Files.readString(jsonOutput);
        String html = Files.readString(output);

        assertTrue(json.contains("\"ruleGuidance\""));
        assertTrue(json.contains("\"ruleDomainSelection\""));
        assertTrue(json.contains("\"ruleId\": \"RULE_A\""));
        assertTrue(json.contains("\"ruleId\": \"RULE_B\""));
        assertTrue(json.contains("\"domain\": \"GENERAL\""));
        assertTrue(json.contains("\"findingCount\": 1"));
        assertTrue(json.contains("\"staleEntryCount\": 1"));
        assertTrue(json.contains("\"enabledDomains\""));
        assertTrue(json.contains("\"EVENTS\""));
        assertTrue(json.contains("\"enabledRuleIds\""));
        assertTrue(json.contains("\"effectiveRuleIds\""));
        assertTrue(json.contains("\"effectiveRuleBreakdown\""));
        assertTrue(json.contains("Refactor the affected code path."));
        assertTrue(html.contains("baseline diff"));
        assertTrue(html.contains("<h2>Modules</h2>"));
        assertTrue(html.contains("root-app"));
        assertTrue(html.contains("New Issues"));
        assertTrue(html.contains("Stale Baseline Entries"));
        assertTrue(html.contains("Enabled rule domains"));
        assertTrue(html.contains("Disabled rule domains"));
        assertTrue(html.contains("Effective rule domains"));
        assertTrue(html.contains("Enabled rule ids"));
        assertTrue(html.contains("Disabled rule ids"));
        assertTrue(html.contains("Effective rule ids"));
        assertTrue(html.contains("Effective Rules By Domain"));
        assertTrue(html.contains("<h2>Rule Guidance</h2>"));
        assertTrue(html.contains("href=\"#rule-rule-a\""));
        assertTrue(html.contains("href=\"#rule-rule-b\""));
        assertTrue(html.contains("Current new issues"));
        assertTrue(html.contains("Refactor the affected code path."));
    }
}
