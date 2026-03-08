/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.Map;

final class LintReportJsonWriter {

    String write(LintReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"generatedAt\": \"").append(report.generatedAt()).append("\",\n");
        builder.append("  \"projectRoot\": \"").append(ReportWriterSupport.escapeJson(report.projectRoot().toString())).append("\",\n");
        builder.append("  \"sourceDirectory\": \"").append(ReportWriterSupport.escapeJson(report.sourceDirectory().toString())).append("\",\n");
        builder.append("  \"sourceDirectories\": [\n");
        for (int index = 0; index < report.sourceDirectories().size(); index++) {
            builder.append("    \"").append(ReportWriterSupport.escapeJson(report.sourceDirectories().get(index).toString())).append("\"");
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
            builder.append("      \"id\": \"").append(ReportWriterSupport.escapeJson(rule.id())).append("\",\n");
            builder.append("      \"title\": \"").append(ReportWriterSupport.escapeJson(rule.title())).append("\",\n");
            builder.append("      \"description\": \"").append(ReportWriterSupport.escapeJson(rule.description())).append("\",\n");
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
            builder.append("      \"moduleId\": \"").append(ReportWriterSupport.escapeJson(moduleSummary.moduleId())).append("\",\n");
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
            builder.append("      \"module\": \"").append(ReportWriterSupport.escapeJson(report.moduleFor(issue.file()))).append("\",\n");
            builder.append("      \"ruleId\": \"").append(ReportWriterSupport.escapeJson(issue.ruleId())).append("\",\n");
            builder.append("      \"severity\": \"").append(issue.severity()).append("\",\n");
            builder.append("      \"message\": \"").append(ReportWriterSupport.escapeJson(issue.message())).append("\",\n");
            builder.append("      \"file\": \"").append(ReportWriterSupport.escapeJson(issue.file().toString())).append("\",\n");
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
            builder.append("      \"file\": \"").append(ReportWriterSupport.escapeJson(ReportWriterSupport.relativePath(report.projectRoot(), parseProblem.file()))).append("\",\n");
            builder.append("      \"messages\": [\n");
            for (int messageIndex = 0; messageIndex < parseProblem.messages().size(); messageIndex++) {
                builder.append("        \"").append(ReportWriterSupport.escapeJson(parseProblem.messages().get(messageIndex))).append("\"");
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
}
