/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.Comparator;

final class BaselineDiffHtmlWriter {

    String write(BaselineDiffReport baselineDiffReport) {
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
                        .append("<td><code>").append(ReportWriterSupport.escapeHtml(moduleSummary.moduleId())).append("</code></td>")
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
                        .append("<td><code>").append(ReportWriterSupport.escapeHtml(baselineDiffReport.moduleForIssue(issue))).append("</code></td>")
                        .append("<td><code>").append(ReportWriterSupport.escapeHtml(issue.ruleId())).append("</code></td>")
                        .append("<td>").append(issue.severity()).append("</td>")
                        .append("<td>").append(ReportWriterSupport.escapeHtml(issue.file().toString())).append("</td>")
                        .append("<td>").append(issue.line()).append("</td>")
                        .append("<td>").append(ReportWriterSupport.escapeHtml(issue.message())).append("</td>")
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
                        .append("<td><code>").append(ReportWriterSupport.escapeHtml(baselineDiffReport.moduleForEntry(staleEntry))).append("</code></td>")
                        .append("<td><code>").append(ReportWriterSupport.escapeHtml(staleEntry.ruleId())).append("</code></td>")
                        .append("<td>").append(ReportWriterSupport.escapeHtml(staleEntry.relativePath())).append("</td>")
                        .append("<td>").append(staleEntry.line()).append("</td>")
                        .append("<td>").append(ReportWriterSupport.escapeHtml(staleEntry.message())).append("</td>")
                        .append("</tr>\n");
            }
            builder.append("      </tbody>\n");
            builder.append("    </table>\n");
        }
        builder.append("  </section>\n");
        builder.append("</body>\n</html>\n");
        return builder.toString();
    }
}
