/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

public final class ReportWriter {

    public void writeJson(LintReport report, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, toJson(report));
    }

    public void writeHtml(LintReport report, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, toHtml(report));
    }

    public void writeSarif(LintReport report, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, toSarif(report));
    }

    public void writeBaselineDiff(BaselineDiffReport baselineDiffReport, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, toBaselineDiffJson(baselineDiffReport));
    }

    public void writeBaselineDiffHtml(BaselineDiffReport baselineDiffReport, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, toBaselineDiffHtml(baselineDiffReport));
    }

    public void writeRulesMarkdown(Iterable<RuleDescriptor> rules, Path outputFile) throws IOException {
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputFile, toRulesMarkdown(rules));
    }

    private String toJson(LintReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"generatedAt\": \"").append(report.generatedAt()).append("\",\n");
        builder.append("  \"projectRoot\": \"").append(escapeJson(report.projectRoot().toString())).append("\",\n");
        builder.append("  \"sourceDirectory\": \"").append(escapeJson(report.sourceDirectory().toString())).append("\",\n");
        builder.append("  \"sourceDirectories\": [\n");
        for (int index = 0; index < report.sourceDirectories().size(); index++) {
            builder.append("    \"").append(escapeJson(report.sourceDirectories().get(index).toString())).append("\"");
            if (index < report.sourceDirectories().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ],\n");
        builder.append("  \"summary\": {\n");
        builder.append("    \"issueCount\": ").append(report.issueCount()).append(",\n");
        builder.append("    \"ruleCount\": ").append(report.rules().size()).append(",\n");
        builder.append("    \"sourceDirectoryCount\": ").append(report.sourceDirectoryCount()).append(",\n");
        builder.append("    \"suppressedIssueCount\": ").append(report.suppressedIssueCount()).append(",\n");
        builder.append("    \"baselineMatchedIssueCount\": ").append(report.baselineMatchedIssueCount()).append(",\n");
        builder.append("    \"staleBaselineEntryCount\": ").append(report.staleBaselineEntryCount()).append(",\n");
        builder.append("    \"cachedFileCount\": ").append(report.cachedFileCount()).append(",\n");
        builder.append("    \"parseProblemFileCount\": ").append(report.parseProblemFileCount()).append(",\n");
        builder.append("    \"severities\": {");

        boolean firstSeverity = true;
        for (Map.Entry<LintSeverity, Long> entry : report.severityCounts().entrySet()) {
            if (!firstSeverity) {
                builder.append(',');
            }
            builder.append("\n      \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            firstSeverity = false;
        }
        if (!firstSeverity) {
            builder.append('\n').append("    ");
        }
        builder.append("}\n");
        builder.append("  },\n");
        builder.append("  \"rules\": [\n");
        for (int index = 0; index < report.rules().size(); index++) {
            RuleDescriptor rule = report.rules().get(index);
            builder.append("    {\n");
            builder.append("      \"id\": \"").append(escapeJson(rule.id())).append("\",\n");
            builder.append("      \"title\": \"").append(escapeJson(rule.title())).append("\",\n");
            builder.append("      \"description\": \"").append(escapeJson(rule.description())).append("\",\n");
            builder.append("      \"defaultSeverity\": \"").append(rule.defaultSeverity()).append("\"\n");
            builder.append("    }");
            if (index < report.rules().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ],\n");
        builder.append("  \"moduleSummaries\": [\n");
        for (int index = 0; index < report.moduleSummaries().size(); index++) {
            ModuleSummary moduleSummary = report.moduleSummaries().get(index);
            builder.append("    {\n");
            builder.append("      \"moduleId\": \"").append(escapeJson(moduleSummary.moduleId())).append("\",\n");
            builder.append("      \"sourceDirectoryCount\": ").append(moduleSummary.sourceDirectoryCount()).append(",\n");
            builder.append("      \"sourceFileCount\": ").append(moduleSummary.sourceFileCount()).append(",\n");
            builder.append("      \"visibleIssueCount\": ").append(moduleSummary.visibleIssueCount()).append(",\n");
            builder.append("      \"parseProblemFileCount\": ").append(moduleSummary.parseProblemFileCount()).append(",\n");
            builder.append("      \"cachedFileCount\": ").append(moduleSummary.cachedFileCount()).append('\n');
            builder.append("    }");
            if (index < report.moduleSummaries().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ],\n");
        builder.append("  \"issues\": [\n");

        for (int index = 0; index < report.issues().size(); index++) {
            LintIssue issue = report.issues().get(index);
            builder.append("    {\n");
            builder.append("      \"module\": \"").append(escapeJson(report.moduleFor(issue.file()))).append("\",\n");
            builder.append("      \"ruleId\": \"").append(escapeJson(issue.ruleId())).append("\",\n");
            builder.append("      \"severity\": \"").append(issue.severity()).append("\",\n");
            builder.append("      \"message\": \"").append(escapeJson(issue.message())).append("\",\n");
            builder.append("      \"file\": \"").append(escapeJson(issue.file().toString())).append("\",\n");
            builder.append("      \"line\": ").append(issue.line()).append('\n');
            builder.append("    }");
            if (index < report.issues().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }

        builder.append("  ]\n");
        builder.append(",\n");
        builder.append("  \"parseProblems\": [\n");
        for (int index = 0; index < report.parseProblems().size(); index++) {
            SourceParseProblem parseProblem = report.parseProblems().get(index);
            builder.append("    {\n");
            builder.append("      \"file\": \"").append(escapeJson(relativePath(report.projectRoot(), parseProblem.file()))).append("\",\n");
            builder.append("      \"messages\": [\n");
            for (int messageIndex = 0; messageIndex < parseProblem.messages().size(); messageIndex++) {
                builder.append("        \"").append(escapeJson(parseProblem.messages().get(messageIndex))).append("\"");
                if (messageIndex < parseProblem.messages().size() - 1) {
                    builder.append(',');
                }
                builder.append('\n');
            }
            builder.append("      ]\n");
            builder.append("    }");
            if (index < report.parseProblems().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String toBaselineDiffJson(BaselineDiffReport baselineDiffReport) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"summary\": {\n");
        builder.append("    \"newIssueCount\": ").append(baselineDiffReport.newIssues().size()).append(",\n");
        builder.append("    \"matchedBaselineCount\": ").append(baselineDiffReport.matchedEntries().size()).append(",\n");
        builder.append("    \"staleBaselineCount\": ").append(baselineDiffReport.staleEntries().size()).append('\n');
        builder.append("  },\n");
        builder.append("  \"moduleSummaries\": [\n");
        for (int index = 0; index < baselineDiffReport.moduleSummaries().size(); index++) {
            BaselineDiffModuleSummary moduleSummary = baselineDiffReport.moduleSummaries().get(index);
            builder.append("    {\n");
            builder.append("      \"moduleId\": \"").append(escapeJson(moduleSummary.moduleId())).append("\",\n");
            builder.append("      \"newIssueCount\": ").append(moduleSummary.newIssueCount()).append(",\n");
            builder.append("      \"matchedBaselineCount\": ").append(moduleSummary.matchedBaselineCount()).append(",\n");
            builder.append("      \"staleBaselineCount\": ").append(moduleSummary.staleBaselineCount()).append('\n');
            builder.append("    }");
            if (index < baselineDiffReport.moduleSummaries().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ],\n");
        builder.append("  \"newIssues\": [\n");
        for (int index = 0; index < baselineDiffReport.newIssues().size(); index++) {
            LintIssue issue = baselineDiffReport.newIssues().get(index);
            builder.append("    {\n");
            builder.append("      \"module\": \"").append(escapeJson(baselineDiffReport.moduleForIssue(issue))).append("\",\n");
            builder.append("      \"ruleId\": \"").append(escapeJson(issue.ruleId())).append("\",\n");
            builder.append("      \"file\": \"").append(escapeJson(issue.file().toString())).append("\",\n");
            builder.append("      \"line\": ").append(issue.line()).append(",\n");
            builder.append("      \"message\": \"").append(escapeJson(issue.message())).append("\"\n");
            builder.append("    }");
            if (index < baselineDiffReport.newIssues().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ],\n");
        builder.append("  \"staleEntries\": [\n");
        java.util.List<BaselineEntry> staleEntries = baselineDiffReport.staleEntries().stream()
                .sorted(Comparator.comparing(BaselineEntry::relativePath)
                        .thenComparingInt(BaselineEntry::line)
                        .thenComparing(BaselineEntry::ruleId))
                .toList();
        int staleIndex = 0;
        for (BaselineEntry staleEntry : staleEntries) {
            builder.append("    {\n");
            builder.append("      \"module\": \"").append(escapeJson(baselineDiffReport.moduleForEntry(staleEntry))).append("\",\n");
            builder.append("      \"ruleId\": \"").append(escapeJson(staleEntry.ruleId())).append("\",\n");
            builder.append("      \"relativePath\": \"").append(escapeJson(staleEntry.relativePath())).append("\",\n");
            builder.append("      \"line\": ").append(staleEntry.line()).append(",\n");
            builder.append("      \"message\": \"").append(escapeJson(staleEntry.message())).append("\"\n");
            builder.append("    }");
            if (staleIndex < staleEntries.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
            staleIndex++;
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String toBaselineDiffHtml(BaselineDiffReport baselineDiffReport) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html lang=\"en\">\n<head>\n");
        builder.append("  <meta charset=\"UTF-8\" />\n");
        builder.append("  <title>spring-correctness-linter baseline diff</title>\n");
        builder.append("  <style>body{font-family:Arial,sans-serif;margin:24px;}table{border-collapse:collapse;width:100%;margin-bottom:24px;}th,td{border:1px solid #ddd;padding:8px;text-align:left;}th{background:#f4f4f4;}code{background:#f6f8fa;padding:2px 4px;border-radius:4px;}section{margin-bottom:32px;}h1,h2{margin-bottom:12px;}ul{padding-left:20px;}</style>\n");
        builder.append("</head>\n<body>\n");
        builder.append("  <h1>spring-correctness-linter baseline diff</h1>\n");
        builder.append("  <p><strong>New issues:</strong> ").append(baselineDiffReport.newIssues().size()).append("</p>\n");
        builder.append("  <p><strong>Matched baseline entries:</strong> ").append(baselineDiffReport.matchedEntries().size()).append("</p>\n");
        builder.append("  <p><strong>Stale baseline entries:</strong> ").append(baselineDiffReport.staleEntries().size()).append("</p>\n");

        if (!baselineDiffReport.moduleSummaries().isEmpty()) {
            builder.append("  <section>\n");
            builder.append("    <h2>Modules</h2>\n");
            builder.append("    <table>\n");
            builder.append("      <thead><tr><th>Module</th><th>New issues</th><th>Matched baseline</th><th>Stale baseline</th></tr></thead>\n");
            builder.append("      <tbody>\n");
            for (BaselineDiffModuleSummary moduleSummary : baselineDiffReport.moduleSummaries()) {
                builder.append("        <tr>")
                        .append("<td><code>").append(escapeHtml(moduleSummary.moduleId())).append("</code></td>")
                        .append("<td>").append(moduleSummary.newIssueCount()).append("</td>")
                        .append("<td>").append(moduleSummary.matchedBaselineCount()).append("</td>")
                        .append("<td>").append(moduleSummary.staleBaselineCount()).append("</td>")
                        .append("</tr>\n");
            }
            builder.append("      </tbody>\n");
            builder.append("    </table>\n");
            builder.append("  </section>\n");
        }

        builder.append("  <section>\n");
        builder.append("    <h2>New Issues</h2>\n");
        if (baselineDiffReport.newIssues().isEmpty()) {
            builder.append("    <p>No new issues.</p>\n");
        } else {
            builder.append("    <table>\n");
            builder.append("      <thead><tr><th>Module</th><th>Rule</th><th>Severity</th><th>File</th><th>Line</th><th>Message</th></tr></thead>\n");
            builder.append("      <tbody>\n");
            for (LintIssue issue : baselineDiffReport.newIssues()) {
                builder.append("        <tr>")
                        .append("<td><code>").append(escapeHtml(baselineDiffReport.moduleForIssue(issue))).append("</code></td>")
                        .append("<td><code>").append(escapeHtml(issue.ruleId())).append("</code></td>")
                        .append("<td>").append(issue.severity()).append("</td>")
                        .append("<td>").append(escapeHtml(issue.file().toString())).append("</td>")
                        .append("<td>").append(issue.line()).append("</td>")
                        .append("<td>").append(escapeHtml(issue.message())).append("</td>")
                        .append("</tr>\n");
            }
            builder.append("      </tbody>\n");
            builder.append("    </table>\n");
        }
        builder.append("  </section>\n");

        builder.append("  <section>\n");
        builder.append("    <h2>Stale Baseline Entries</h2>\n");
        java.util.List<BaselineEntry> staleEntries = baselineDiffReport.staleEntries().stream()
                .sorted(Comparator.comparing(BaselineEntry::relativePath)
                        .thenComparingInt(BaselineEntry::line)
                        .thenComparing(BaselineEntry::ruleId))
                .toList();
        if (staleEntries.isEmpty()) {
            builder.append("    <p>No stale baseline entries.</p>\n");
        } else {
            builder.append("    <table>\n");
            builder.append("      <thead><tr><th>Module</th><th>Rule</th><th>Relative Path</th><th>Line</th><th>Message</th></tr></thead>\n");
            builder.append("      <tbody>\n");
            for (BaselineEntry staleEntry : staleEntries) {
                builder.append("        <tr>")
                        .append("<td><code>").append(escapeHtml(baselineDiffReport.moduleForEntry(staleEntry))).append("</code></td>")
                        .append("<td><code>").append(escapeHtml(staleEntry.ruleId())).append("</code></td>")
                        .append("<td>").append(escapeHtml(staleEntry.relativePath())).append("</td>")
                        .append("<td>").append(staleEntry.line()).append("</td>")
                        .append("<td>").append(escapeHtml(staleEntry.message())).append("</td>")
                        .append("</tr>\n");
            }
            builder.append("      </tbody>\n");
            builder.append("    </table>\n");
        }
        builder.append("  </section>\n");
        builder.append("</body>\n</html>\n");
        return builder.toString();
    }

    private String toHtml(LintReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html lang=\"en\">\n<head>\n");
        builder.append("  <meta charset=\"UTF-8\" />\n");
        builder.append("  <title>spring-correctness-linter report</title>\n");
        builder.append("  <style>body{font-family:Arial,sans-serif;margin:24px;}table{border-collapse:collapse;width:100%;}th,td{border:1px solid #ddd;padding:8px;text-align:left;}th{background:#f4f4f4;}code{background:#f6f8fa;padding:2px 4px;border-radius:4px;}</style>\n");
        builder.append("</head>\n<body>\n");
        builder.append("  <h1>spring-correctness-linter report</h1>\n");
        builder.append("  <p><strong>Generated:</strong> ").append(report.generatedAt()).append("</p>\n");
        builder.append("  <p><strong>Source roots:</strong> ").append(report.sourceDirectoryCount()).append("</p>\n");
        builder.append("  <p><strong>Rules:</strong> ").append(report.rules().size()).append("</p>\n");
        builder.append("  <p><strong>Visible issues:</strong> ").append(report.issueCount()).append("</p>\n");
        builder.append("  <p><strong>Suppressed inline:</strong> ").append(report.suppressedIssueCount()).append("</p>\n");
        builder.append("  <p><strong>Matched baseline:</strong> ").append(report.baselineMatchedIssueCount()).append("</p>\n");
        builder.append("  <p><strong>Stale baseline entries:</strong> ").append(report.staleBaselineEntryCount()).append("</p>\n");
        builder.append("  <p><strong>Cached files:</strong> ").append(report.cachedFileCount()).append("</p>\n");
        builder.append("  <p><strong>Files with parse problems:</strong> ").append(report.parseProblemFileCount()).append("</p>\n");
        if (!report.moduleSummaries().isEmpty()) {
            builder.append("  <h2>Modules</h2>\n");
            builder.append("  <table>\n");
            builder.append("    <thead><tr><th>Module</th><th>Source roots</th><th>Source files</th><th>Visible issues</th><th>Parse problem files</th><th>Cached files</th></tr></thead>\n");
            builder.append("    <tbody>\n");
            for (ModuleSummary moduleSummary : report.moduleSummaries()) {
                builder.append("      <tr>")
                        .append("<td><code>").append(escapeHtml(moduleSummary.moduleId())).append("</code></td>")
                        .append("<td>").append(moduleSummary.sourceDirectoryCount()).append("</td>")
                        .append("<td>").append(moduleSummary.sourceFileCount()).append("</td>")
                        .append("<td>").append(moduleSummary.visibleIssueCount()).append("</td>")
                        .append("<td>").append(moduleSummary.parseProblemFileCount()).append("</td>")
                        .append("<td>").append(moduleSummary.cachedFileCount()).append("</td>")
                        .append("</tr>\n");
            }
            builder.append("    </tbody>\n");
            builder.append("  </table>\n");
        }
        builder.append("  <table>\n");
        builder.append("    <thead><tr><th>Module</th><th>Rule</th><th>Severity</th><th>File</th><th>Line</th><th>Message</th></tr></thead>\n");
        builder.append("    <tbody>\n");
        for (LintIssue issue : report.issues()) {
            builder.append("      <tr>");
            builder.append("<td><code>").append(escapeHtml(report.moduleFor(issue.file()))).append("</code></td>");
            builder.append("<td><code>").append(escapeHtml(issue.ruleId())).append("</code></td>");
            builder.append("<td>").append(issue.severity()).append("</td>");
            builder.append("<td>").append(escapeHtml(issue.file().toString())).append("</td>");
            builder.append("<td>").append(issue.line()).append("</td>");
            builder.append("<td>").append(escapeHtml(issue.message())).append("</td>");
            builder.append("</tr>\n");
        }
        builder.append("    </tbody>\n");
        builder.append("  </table>\n");
        if (!report.parseProblems().isEmpty()) {
            builder.append("  <h2>Source Parse Problems</h2>\n");
            builder.append("  <ul>\n");
            for (SourceParseProblem parseProblem : report.parseProblems()) {
                builder.append("    <li><strong>")
                        .append(escapeHtml(relativePath(report.projectRoot(), parseProblem.file())))
                        .append("</strong>");
                if (!parseProblem.messages().isEmpty()) {
                    builder.append(": ").append(escapeHtml(parseProblem.messages().get(0)));
                }
                builder.append("</li>\n");
            }
            builder.append("  </ul>\n");
        }
        builder.append("</body>\n</html>\n");
        return builder.toString();
    }

    private String toSarif(LintReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"$schema\": \"https://json.schemastore.org/sarif-2.1.0.json\",\n");
        builder.append("  \"version\": \"2.1.0\",\n");
        builder.append("  \"runs\": [\n");
        builder.append("    {\n");
        builder.append("      \"tool\": {\n");
        builder.append("        \"driver\": {\n");
        builder.append("          \"name\": \"spring-correctness-linter\",\n");
        builder.append("          \"rules\": [\n");
        for (int index = 0; index < report.rules().size(); index++) {
            RuleDescriptor rule = report.rules().get(index);
            builder.append("            {\n");
            builder.append("              \"id\": \"").append(escapeJson(rule.id())).append("\",\n");
            builder.append("              \"name\": \"").append(escapeJson(rule.title())).append("\",\n");
            builder.append("              \"shortDescription\": { \"text\": \"").append(escapeJson(rule.title())).append("\" },\n");
            builder.append("              \"fullDescription\": { \"text\": \"").append(escapeJson(rule.description())).append("\" },\n");
            builder.append("              \"defaultConfiguration\": { \"level\": \"").append(toSarifLevel(rule.defaultSeverity())).append("\" }\n");
            builder.append("            }");
            if (index < report.rules().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("          ]\n");
        builder.append("        }\n");
        builder.append("      },\n");
        builder.append("      \"results\": [\n");
        for (int index = 0; index < report.issues().size(); index++) {
            LintIssue issue = report.issues().get(index);
            builder.append("        {\n");
            builder.append("          \"ruleId\": \"").append(escapeJson(issue.ruleId())).append("\",\n");
            builder.append("          \"level\": \"").append(toSarifLevel(issue.severity())).append("\",\n");
            builder.append("          \"message\": { \"text\": \"").append(escapeJson(issue.message())).append("\" },\n");
            builder.append("          \"locations\": [\n");
            builder.append("            {\n");
            builder.append("              \"physicalLocation\": {\n");
            builder.append("                \"artifactLocation\": { \"uri\": \"").append(escapeJson(relativePath(report.projectRoot(), issue.file()))).append("\" },\n");
            builder.append("                \"region\": { \"startLine\": ").append(issue.line()).append(" }\n");
            builder.append("              }\n");
            builder.append("            }\n");
            builder.append("          ]\n");
            builder.append("        }");
            if (index < report.issues().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("      ]\n");
        builder.append("    }\n");
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String toRulesMarkdown(Iterable<RuleDescriptor> rules) {
        java.util.List<RuleDescriptor> descriptors = new java.util.ArrayList<>();
        for (RuleDescriptor rule : rules) {
            descriptors.add(rule);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# Generated Rule Reference\n\n");
        builder.append("This file is generated from the current rule registry. Use it as the default rule index for CLI, Maven, and CI integration.\n\n");
        builder.append("## Usage\n\n");
        builder.append("- Disable rules: `-Dspring.correctness.linter.disabledRules=RULE_A,RULE_B`\n");
        builder.append("- Enable only selected rules: `-Dspring.correctness.linter.enabledRules=RULE_A,RULE_B`\n");
        builder.append("- Override severities: `-Dspring.correctness.linter.severityOverrides=RULE_A=ERROR,RULE_B=INFO`\n");
        builder.append("- Suppress one finding: `// spring-correctness-linter:disable-next-line RULE_ID reason: explanation`\n\n");
        builder.append("## Rule Index\n\n");
        builder.append("| Rule ID | Default Severity | Title |\n");
        builder.append("| --- | --- | --- |\n");
        for (RuleDescriptor rule : descriptors) {
            builder.append("| `").append(rule.id()).append("` | `")
                    .append(rule.defaultSeverity()).append("` | ")
                    .append(rule.title()).append(" |\n");
        }
        builder.append("\n## Rule Details\n\n");
        for (RuleDescriptor rule : descriptors) {
            builder.append("### `").append(rule.id()).append("`\n");
            builder.append("- Title: ").append(rule.title()).append("\n");
            builder.append("- Default severity: `").append(rule.defaultSeverity()).append("`\n");
            builder.append("- Description: ").append(rule.description()).append("\n");
            builder.append("- Disable: `-Dspring.correctness.linter.disabledRules=").append(rule.id()).append("`\n");
            builder.append("- Run only this rule: `-Dspring.correctness.linter.enabledRules=").append(rule.id()).append("`\n");
            builder.append("- Override severity: `-Dspring.correctness.linter.severityOverrides=").append(rule.id()).append("=ERROR`\n");
            builder.append("- Suppress next line: `// spring-correctness-linter:disable-next-line ").append(rule.id()).append(" reason: explain why`\n");
            builder.append("- Suppress next method: `// spring-correctness-linter:disable-next-method ").append(rule.id()).append(" reason: explain why`\n\n");
        }
        return builder.toString();
    }

    private String relativePath(Path projectRoot, Path file) {
        try {
            return projectRoot.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (IllegalArgumentException exception) {
            return file.toString().replace('\\', '/');
        }
    }

    private String toSarifLevel(LintSeverity severity) {
        return switch (severity) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFO -> "note";
        };
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
