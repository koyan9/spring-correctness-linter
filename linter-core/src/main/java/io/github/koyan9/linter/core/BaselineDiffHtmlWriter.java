/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class BaselineDiffHtmlWriter {

    String write(BaselineDiffReport baselineDiffReport) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html lang=\"en\">\n<head>\n");
        builder.append("  <meta charset=\"UTF-8\" />\n");
        builder.append("  <title>spring-correctness-linter baseline diff</title>\n");
        builder.append("  <style>body{font-family:Arial,sans-serif;margin:24px;}table{border-collapse:collapse;width:100%;margin-bottom:24px;}th,td{border:1px solid #ddd;padding:8px;text-align:left;}th{background:#f4f4f4;}code{background:#f6f8fa;padding:2px 4px;border-radius:4px;}section{margin-bottom:32px;}h1,h2{margin-bottom:12px;}ul{padding-left:20px;}a{color:#0969da;text-decoration:none;}a:hover{text-decoration:underline;}.rule-guidance{border:1px solid #ddd;border-radius:8px;padding:16px;margin:16px 0;background:#fafbfc;}</style>\n");
        builder.append("</head>\n<body>\n");
        builder.append("  <a id=\"top\"></a>\n");
        builder.append("  <h1>spring-correctness-linter baseline diff</h1>\n");
        builder.append("  <p><strong>New issues:</strong> ").append(baselineDiffReport.newIssues().size()).append("</p>\n");
        builder.append("  <p><strong>Matched baseline entries:</strong> ").append(baselineDiffReport.matchedEntries().size()).append("</p>\n");
        builder.append("  <p><strong>Stale baseline entries:</strong> ").append(baselineDiffReport.staleEntries().size()).append("</p>\n");
        builder.append("  <p><strong>Enabled rule domains:</strong> ").append(formatDomains(baselineDiffReport.ruleDomainSelection().enabledDomains(), "all")).append("</p>\n");
        builder.append("  <p><strong>Disabled rule domains:</strong> ").append(formatDomains(baselineDiffReport.ruleDomainSelection().disabledDomains(), "none")).append("</p>\n");
        builder.append("  <p><strong>Effective rule domains:</strong> ").append(formatDomains(baselineDiffReport.ruleDomainSelection().effectiveDomains(), "none")).append("</p>\n");
        builder.append("  <p><strong>Enabled rule ids:</strong> ").append(formatRuleIds(baselineDiffReport.ruleDomainSelection().enabledRuleIds(), "all")).append("</p>\n");
        builder.append("  <p><strong>Disabled rule ids:</strong> ").append(formatRuleIds(baselineDiffReport.ruleDomainSelection().disabledRuleIds(), "none")).append("</p>\n");
        builder.append("  <p><strong>Effective rule ids:</strong> ").append(formatRuleIds(baselineDiffReport.ruleDomainSelection().effectiveRuleIds(), "none")).append("</p>\n");
        builder.append("  <h2>Effective Rules By Domain</h2>\n");
        appendRuleBreakdownTable(builder, baselineDiffReport.ruleDomainSelection().effectiveRuleBreakdown());

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
                        .append("<td>");
                appendRuleLink(builder, baselineDiffReport, issue.ruleId());
                builder.append("</td>")
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
                        .append("<td>");
                appendRuleLink(builder, baselineDiffReport, staleEntry.ruleId());
                builder.append("</td>")
                        .append("<td>").append(ReportWriterSupport.escapeHtml(staleEntry.relativePath())).append("</td>")
                        .append("<td>").append(staleEntry.line()).append("</td>")
                        .append("<td>").append(ReportWriterSupport.escapeHtml(staleEntry.message())).append("</td>")
                        .append("</tr>\n");
            }
            builder.append("      </tbody>\n");
            builder.append("    </table>\n");
        }
        builder.append("  </section>\n");

        appendRuleGuidanceSection(builder, baselineDiffReport);
        builder.append("</body>\n</html>\n");
        return builder.toString();
    }

    private void appendRuleGuidanceSection(StringBuilder builder, BaselineDiffReport baselineDiffReport) {
        Set<String> relevantRuleIds = baselineDiffReport.newIssues().stream()
                .map(LintIssue::ruleId)
                .collect(Collectors.toSet());
        baselineDiffReport.staleEntries().stream()
                .map(BaselineEntry::ruleId)
                .forEach(relevantRuleIds::add);

        java.util.List<RuleDescriptor> relevantRules = baselineDiffReport.rules().stream()
                .filter(rule -> relevantRuleIds.contains(rule.id()))
                .toList();
        if (relevantRules.isEmpty()) {
            return;
        }

        builder.append("  <section>\n");
        builder.append("    <h2>Rule Guidance</h2>\n");
        for (RuleDescriptor rule : relevantRules) {
            builder.append("    <div class=\"rule-guidance\" id=\"")
                    .append(ReportWriterSupport.escapeHtml(ruleAnchorId(rule.id())))
                    .append("\">\n");
            builder.append("      <h3><code>")
                    .append(ReportWriterSupport.escapeHtml(rule.id()))
                    .append("</code> - ")
                    .append(ReportWriterSupport.escapeHtml(rule.title()))
                    .append("</h3>\n");
            builder.append("      <p><strong>Default severity:</strong> ")
                    .append(rule.defaultSeverity())
                    .append("</p>\n");
            builder.append("      <p><strong>Current new issues:</strong> ")
                    .append(currentNewIssueCount(baselineDiffReport, rule.id()))
                    .append("</p>\n");
            builder.append("      <p><strong>Description:</strong> ")
                    .append(ReportWriterSupport.escapeHtml(rule.description()))
                    .append("</p>\n");
            appendGuidanceList(builder, "Applies when", rule.appliesWhen());
            appendGuidanceList(builder, "Common false-positive boundaries", rule.commonFalsePositiveBoundaries());
            appendGuidanceList(builder, "Recommended fixes", rule.recommendedFixes());
            builder.append("      <p><a href=\"#top\">Back to top</a></p>\n");
            builder.append("    </div>\n");
        }
        builder.append("  </section>\n");
    }

    private void appendRuleLink(StringBuilder builder, BaselineDiffReport baselineDiffReport, String ruleId) {
        if (baselineDiffReport.rules().stream().noneMatch(rule -> rule.id().equals(ruleId))) {
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
        builder.append("      <h4>")
                .append(ReportWriterSupport.escapeHtml(title))
                .append("</h4>\n");
        builder.append("      <ul>\n");
        for (String item : items) {
            builder.append("        <li>")
                    .append(ReportWriterSupport.escapeHtml(item))
                    .append("</li>\n");
        }
        builder.append("      </ul>\n");
    }

    private long currentNewIssueCount(BaselineDiffReport baselineDiffReport, String ruleId) {
        return baselineDiffReport.newIssues().stream().filter(issue -> issue.ruleId().equals(ruleId)).count();
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
