/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.Locale;

final class LintReportHtmlWriter {

    String write(LintReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html lang=\"en\">\n<head>\n");
        builder.append("  <meta charset=\"UTF-8\" />\n");
        builder.append("  <title>spring-correctness-linter report</title>\n");
        builder.append("  <style>body{font-family:Arial,sans-serif;margin:24px;}table{border-collapse:collapse;width:100%;}th,td{border:1px solid #ddd;padding:8px;text-align:left;}th{background:#f4f4f4;}code{background:#f6f8fa;padding:2px 4px;border-radius:4px;}a{color:#0969da;text-decoration:none;}a:hover{text-decoration:underline;}.rule-guidance{border:1px solid #ddd;border-radius:8px;padding:16px;margin:16px 0;background:#fafbfc;}</style>\n");
        builder.append("</head>\n<body>\n");
        builder.append("  <a id=\"top\"></a>\n");
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
        builder.append("  <p><strong>Enabled rule domains:</strong> ").append(formatDomains(report.ruleDomainSelection().enabledDomains(), "all")).append("</p>\n");
        builder.append("  <p><strong>Disabled rule domains:</strong> ").append(formatDomains(report.ruleDomainSelection().disabledDomains(), "none")).append("</p>\n");
        builder.append("  <p><strong>Effective rule domains:</strong> ").append(formatDomains(report.ruleDomainSelection().effectiveDomains(), "none")).append("</p>\n");
        builder.append("  <p><strong>Enabled rule ids:</strong> ").append(formatRuleIds(report.ruleDomainSelection().enabledRuleIds(), "all")).append("</p>\n");
        builder.append("  <p><strong>Disabled rule ids:</strong> ").append(formatRuleIds(report.ruleDomainSelection().disabledRuleIds(), "none")).append("</p>\n");
        builder.append("  <p><strong>Effective rule ids:</strong> ").append(formatRuleIds(report.ruleDomainSelection().effectiveRuleIds(), "none")).append("</p>\n");
        builder.append("  <h2>Effective Rules By Domain</h2>\n");
        appendRuleBreakdownTable(builder, report.ruleDomainSelection().effectiveRuleBreakdown());
        builder.append("  <h2>Runtime Metrics</h2>\n");
        builder.append("  <p><strong>Total runtime:</strong> ").append(report.runtimeMetrics().totalElapsedMillis()).append(" ms</p>\n");
        builder.append("  <p><strong>Source files:</strong> ").append(report.runtimeMetrics().sourceFileCount()).append("</p>\n");
        builder.append("  <p><strong>Analyzed files:</strong> ").append(report.runtimeMetrics().analyzedFileCount()).append("</p>\n");
        builder.append("  <p><strong>Cache hit rate:</strong> ").append(report.runtimeMetrics().cacheHitRatePercent()).append("%</p>\n");
        builder.append("  <p><strong>Cache scope:</strong> <code>").append(ReportWriterSupport.escapeHtml(report.runtimeMetrics().cacheScope())).append("</code></p>\n");
        if (!report.runtimeMetrics().analysisFingerprint().isBlank()) {
            builder.append("  <p><strong>Analysis fingerprint:</strong> <code>")
                    .append(ReportWriterSupport.escapeHtml(report.runtimeMetrics().analysisFingerprint()))
                    .append("</code></p>\n");
        }
        builder.append("  <table>\n");
        builder.append("    <thead><tr><th>Phase</th><th>Milliseconds</th></tr></thead>\n");
        builder.append("    <tbody>\n");
        appendPhaseRow(builder, "Context load", report.runtimeMetrics().phaseMetrics().contextLoadMillis());
        appendPhaseRow(builder, "Cache load", report.runtimeMetrics().phaseMetrics().cacheLoadMillis());
        appendPhaseRow(builder, "File analysis", report.runtimeMetrics().phaseMetrics().fileAnalysisMillis());
        appendPhaseRow(builder, "Cache write", report.runtimeMetrics().phaseMetrics().cacheWriteMillis());
        appendPhaseRow(builder, "Baseline load", report.runtimeMetrics().phaseMetrics().baselineLoadMillis());
        appendPhaseRow(builder, "Baseline filter", report.runtimeMetrics().phaseMetrics().baselineFilterMillis());
        appendPhaseRow(builder, "Report assembly", report.runtimeMetrics().phaseMetrics().reportAssemblyMillis());
        appendPhaseRow(builder, "Tracked total", report.runtimeMetrics().phaseMetrics().totalTrackedMillis());
        builder.append("    </tbody>\n");
        builder.append("  </table>\n");
        java.util.List<ModuleRuntimeMetrics> slowModules = report.runtimeMetrics().slowestModules(5);
        if (!slowModules.isEmpty()) {
            builder.append("  <h3>Slowest Modules</h3>\n");
            builder.append("  <table>\n");
            builder.append("    <thead><tr><th>Module</th><th>Analyzed ms</th><th>Cache hit rate</th><th>Analyzed files</th><th>Cached files</th></tr></thead>\n");
            builder.append("    <tbody>\n");
            for (ModuleRuntimeMetrics moduleMetric : slowModules) {
                builder.append("      <tr>")
                        .append("<td><code>").append(ReportWriterSupport.escapeHtml(moduleMetric.moduleId())).append("</code></td>")
                        .append("<td>").append(moduleMetric.analyzedMillis()).append("</td>")
                        .append("<td>").append(moduleMetric.cacheHitRatePercent()).append("%</td>")
                        .append("<td>").append(moduleMetric.analyzedFileCount()).append("</td>")
                        .append("<td>").append(moduleMetric.cachedFileCount()).append("</td>")
                        .append("</tr>\n");
            }
            builder.append("    </tbody>\n");
            builder.append("  </table>\n");
        }
        if (!report.moduleSummaries().isEmpty()) {
            builder.append("  <h2>Modules</h2>\n");
            builder.append("  <table>\n");
            builder.append("    <thead><tr><th>Module</th><th>Source roots</th><th>Source files</th><th>Analyzed files</th><th>Visible issues</th><th>Parse problem files</th><th>Cached files</th><th>Cache hit rate</th><th>Analyzed ms</th><th>Cached ms</th><th>Total ms</th></tr></thead>\n");
            builder.append("    <tbody>\n");
            for (ModuleSummary moduleSummary : report.moduleSummaries()) {
                ModuleRuntimeMetrics moduleMetric = report.runtimeMetrics().moduleMetric(moduleSummary.moduleId());
                builder.append("      <tr>")
                        .append("<td><code>").append(ReportWriterSupport.escapeHtml(moduleSummary.moduleId())).append("</code></td>")
                        .append("<td>").append(moduleSummary.sourceDirectoryCount()).append("</td>")
                        .append("<td>").append(moduleSummary.sourceFileCount()).append("</td>")
                        .append("<td>").append(moduleMetric == null ? 0 : moduleMetric.analyzedFileCount()).append("</td>")
                        .append("<td>").append(moduleSummary.visibleIssueCount()).append("</td>")
                        .append("<td>").append(moduleSummary.parseProblemFileCount()).append("</td>")
                        .append("<td>").append(moduleSummary.cachedFileCount()).append("</td>")
                        .append("<td>").append(moduleMetric == null ? 0 : moduleMetric.cacheHitRatePercent()).append("%</td>")
                        .append("<td>").append(moduleMetric == null ? 0 : moduleMetric.analyzedMillis()).append("</td>")
                        .append("<td>").append(moduleMetric == null ? 0 : moduleMetric.cachedMillis()).append("</td>")
                        .append("<td>").append(moduleMetric == null ? 0 : moduleMetric.analysisMillis()).append("</td>")
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
            builder.append("<td>");
            appendRuleLink(builder, report, issue.ruleId());
            builder.append("</td>");
            builder.append("<td>").append(issue.severity()).append("</td>");
            builder.append("<td>").append(ReportWriterSupport.escapeHtml(issue.file().toString())).append("</td>");
            builder.append("<td>").append(issue.line()).append("</td>");
            builder.append("<td>").append(ReportWriterSupport.escapeHtml(issue.message())).append("</td>");
            builder.append("</tr>\n");
        }
        builder.append("    </tbody>\n");
        builder.append("  </table>\n");
        if (!report.rules().isEmpty()) {
            builder.append("  <h2>Rule Guidance</h2>\n");
            for (RuleDescriptor rule : report.rules()) {
                builder.append("  <div class=\"rule-guidance\" id=\"")
                        .append(ReportWriterSupport.escapeHtml(ruleAnchorId(rule.id())))
                        .append("\">\n");
                builder.append("    <h3><code>")
                        .append(ReportWriterSupport.escapeHtml(rule.id()))
                        .append("</code> - ")
                        .append(ReportWriterSupport.escapeHtml(rule.title()))
                        .append("</h3>\n");
                builder.append("    <p><strong>Default severity:</strong> ")
                        .append(rule.defaultSeverity())
                        .append("</p>\n");
                builder.append("    <p><strong>Current visible findings:</strong> ")
                        .append(currentFindingCount(report, rule.id()))
                        .append("</p>\n");
                builder.append("    <p><strong>Description:</strong> ")
                        .append(ReportWriterSupport.escapeHtml(rule.description()))
                        .append("</p>\n");
                appendGuidanceList(builder, "Applies when", rule.appliesWhen());
                appendGuidanceList(builder, "Common false-positive boundaries", rule.commonFalsePositiveBoundaries());
                appendGuidanceList(builder, "Recommended fixes", rule.recommendedFixes());
                builder.append("    <p><a href=\"#top\">Back to top</a></p>\n");
                builder.append("  </div>\n");
            }
        }
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

    private void appendPhaseRow(StringBuilder builder, String phaseName, long millis) {
        builder.append("      <tr><td>")
                .append(ReportWriterSupport.escapeHtml(phaseName))
                .append("</td><td>")
                .append(millis)
                .append("</td></tr>\n");
    }

    private void appendRuleLink(StringBuilder builder, LintReport report, String ruleId) {
        if (report.rules().stream().noneMatch(rule -> rule.id().equals(ruleId))) {
            builder.append("<code>").append(ReportWriterSupport.escapeHtml(ruleId)).append("</code>");
            return;
        }
        builder.append("<a href=\"#")
                .append(ReportWriterSupport.escapeHtml(ruleAnchorId(ruleId)))
                .append("\"><code>")
                .append(ReportWriterSupport.escapeHtml(ruleId))
                .append("</code></a>");
    }

    private void appendGuidanceList(StringBuilder builder, String title, java.util.List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        builder.append("    <h4>")
                .append(ReportWriterSupport.escapeHtml(title))
                .append("</h4>\n");
        builder.append("    <ul>\n");
        for (String item : items) {
            builder.append("      <li>")
                    .append(ReportWriterSupport.escapeHtml(item))
                    .append("</li>\n");
        }
        builder.append("    </ul>\n");
    }

    private long currentFindingCount(LintReport report, String ruleId) {
        return report.issues().stream().filter(issue -> issue.ruleId().equals(ruleId)).count();
    }

    private String ruleAnchorId(String ruleId) {
        return "rule-" + ruleId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String formatDomains(java.util.List<RuleDomain> domains, String emptyLabel) {
        if (domains.isEmpty()) {
            return emptyLabel;
        }
        return domains.stream()
                .map(domain -> "<code>" + ReportWriterSupport.escapeHtml(domain.name()) + "</code>")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String formatRuleIds(java.util.List<String> ruleIds, String emptyLabel) {
        if (ruleIds.isEmpty()) {
            return emptyLabel;
        }
        return ruleIds.stream()
                .map(ruleId -> "<code>" + ReportWriterSupport.escapeHtml(ruleId) + "</code>")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private void appendRuleBreakdownTable(StringBuilder builder, java.util.List<RuleDomainRuleSummary> breakdown) {
        if (breakdown.isEmpty()) {
            builder.append("  <p>No effective rules.</p>\n");
            return;
        }
        builder.append("  <table>\n");
        builder.append("    <thead><tr><th>Domain</th><th>Rule count</th><th>Rule ids</th></tr></thead>\n");
        builder.append("    <tbody>\n");
        for (RuleDomainRuleSummary summary : breakdown) {
            builder.append("      <tr><td><code>")
                    .append(ReportWriterSupport.escapeHtml(summary.domain().name()))
                    .append("</code></td><td>")
                    .append(summary.ruleCount())
                    .append("</td><td>")
                    .append(formatRuleIds(summary.ruleIds(), "none"))
                    .append("</td></tr>\n");
        }
        builder.append("    </tbody>\n");
        builder.append("  </table>\n");
    }
}
