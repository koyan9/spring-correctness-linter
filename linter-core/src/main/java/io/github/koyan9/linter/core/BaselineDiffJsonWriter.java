/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.Comparator;

final class BaselineDiffJsonWriter {

    String write(BaselineDiffReport baselineDiffReport) {
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
            builder.append("      \"moduleId\": \"").append(ReportWriterSupport.escapeJson(moduleSummary.moduleId())).append("\",\n");
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
            builder.append("      \"module\": \"").append(ReportWriterSupport.escapeJson(baselineDiffReport.moduleForIssue(issue))).append("\",\n");
            builder.append("      \"ruleId\": \"").append(ReportWriterSupport.escapeJson(issue.ruleId())).append("\",\n");
            builder.append("      \"file\": \"").append(ReportWriterSupport.escapeJson(issue.file().toString())).append("\",\n");
            builder.append("      \"line\": ").append(issue.line()).append(",\n");
            builder.append("      \"message\": \"").append(ReportWriterSupport.escapeJson(issue.message())).append("\"\n");
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
            builder.append("      \"module\": \"").append(ReportWriterSupport.escapeJson(baselineDiffReport.moduleForEntry(staleEntry))).append("\",\n");
            builder.append("      \"ruleId\": \"").append(ReportWriterSupport.escapeJson(staleEntry.ruleId())).append("\",\n");
            builder.append("      \"relativePath\": \"").append(ReportWriterSupport.escapeJson(staleEntry.relativePath())).append("\",\n");
            builder.append("      \"line\": ").append(staleEntry.line()).append(",\n");
            builder.append("      \"message\": \"").append(ReportWriterSupport.escapeJson(staleEntry.message())).append("\"\n");
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
}
