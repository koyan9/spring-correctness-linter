/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

final class LintReportHtmlWriter {

    String write(LintReport report) {
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
                        .append("<td><code>").append(ReportWriterSupport.escapeHtml(moduleSummary.moduleId())).append("</code></td>")
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
            builder.append("<td><code>").append(ReportWriterSupport.escapeHtml(report.moduleFor(issue.file()))).append("</code></td>");
            builder.append("<td><code>").append(ReportWriterSupport.escapeHtml(issue.ruleId())).append("</code></td>");
            builder.append("<td>").append(issue.severity()).append("</td>");
            builder.append("<td>").append(ReportWriterSupport.escapeHtml(issue.file().toString())).append("</td>");
            builder.append("<td>").append(issue.line()).append("</td>");
            builder.append("<td>").append(ReportWriterSupport.escapeHtml(issue.message())).append("</td>");
            builder.append("</tr>\n");
        }
        builder.append("    </tbody>\n");
        builder.append("  </table>\n");
        if (!report.parseProblems().isEmpty()) {
            builder.append("  <h2>Source Parse Problems</h2>\n");
            builder.append("  <ul>\n");
            for (SourceParseProblem parseProblem : report.parseProblems()) {
                builder.append("    <li><strong>")
                        .append(ReportWriterSupport.escapeHtml(ReportWriterSupport.relativePath(report.projectRoot(), parseProblem.file())))
                        .append("</strong>");
                if (!parseProblem.messages().isEmpty()) {
                    builder.append(": ").append(ReportWriterSupport.escapeHtml(parseProblem.messages().get(0)));
                }
                builder.append("</li>\n");
            }
            builder.append("  </ul>\n");
        }
        builder.append("</body>\n</html>\n");
        return builder.toString();
    }
}
