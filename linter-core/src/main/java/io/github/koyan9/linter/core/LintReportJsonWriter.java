/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;
import java.util.Map;

final class LintReportJsonWriter {

    private final RuleGuidanceJsonSupport ruleGuidanceJsonSupport = new RuleGuidanceJsonSupport();

    String write(LintReport report) {
        return write(report, ReportWriter.ReportDetail.FULL);
    }

    String write(LintReport report, ReportWriter.ReportDetail detail) {
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
        appendRuleDomainSelection(builder, report.ruleDomainSelection());
        if (detail == ReportWriter.ReportDetail.LIGHT) {
            builder.append(",\n");
            appendRuntimeSummary(builder, report, 2);
            builder.append("\n");
            builder.append("}\n");
            return builder.toString();
        }
        builder.append(",\n");
        builder.append("  \"runtimeMetrics\": {\n");
        builder.append("    \"incrementalCacheEnabled\": ").append(report.runtimeMetrics().incrementalCacheEnabled()).append(",\n");
        builder.append("    \"cacheScope\": \"").append(ReportWriterSupport.escapeJson(report.runtimeMetrics().cacheScope())).append("\",\n");
        builder.append("    \"analysisFingerprint\": \"").append(ReportWriterSupport.escapeJson(report.runtimeMetrics().analysisFingerprint())).append("\",\n");
        builder.append("    \"totalElapsedMillis\": ").append(report.runtimeMetrics().totalElapsedMillis()).append(",\n");
        builder.append("    \"sourceFileCount\": ").append(report.runtimeMetrics().sourceFileCount()).append(",\n");
        builder.append("    \"analyzedFileCount\": ").append(report.runtimeMetrics().analyzedFileCount()).append(",\n");
        builder.append("    \"cachedFileCount\": ").append(report.runtimeMetrics().cachedFileCount()).append(",\n");
        builder.append("    \"parseProblemFileCount\": ").append(report.runtimeMetrics().parseProblemFileCount()).append(",\n");
        builder.append("    \"cacheHitRatePercent\": ").append(report.runtimeMetrics().cacheHitRatePercent()).append(",\n");
        appendStringArray(builder, "cacheMissReasons", report.runtimeMetrics().cacheMissReasons(), 4);
        builder.append(",\n");
        builder.append("    \"phaseMetrics\": {\n");
        builder.append("      \"contextLoadMillis\": ").append(report.runtimeMetrics().phaseMetrics().contextLoadMillis()).append(",\n");
        builder.append("      \"cacheLoadMillis\": ").append(report.runtimeMetrics().phaseMetrics().cacheLoadMillis()).append(",\n");
        builder.append("      \"fileAnalysisMillis\": ").append(report.runtimeMetrics().phaseMetrics().fileAnalysisMillis()).append(",\n");
        builder.append("      \"cacheWriteMillis\": ").append(report.runtimeMetrics().phaseMetrics().cacheWriteMillis()).append(",\n");
        builder.append("      \"baselineLoadMillis\": ").append(report.runtimeMetrics().phaseMetrics().baselineLoadMillis()).append(",\n");
        builder.append("      \"baselineFilterMillis\": ").append(report.runtimeMetrics().phaseMetrics().baselineFilterMillis()).append(",\n");
        builder.append("      \"reportAssemblyMillis\": ").append(report.runtimeMetrics().phaseMetrics().reportAssemblyMillis()).append(",\n");
        builder.append("      \"totalTrackedMillis\": ").append(report.runtimeMetrics().phaseMetrics().totalTrackedMillis()).append("\n");
        builder.append("    },\n");
        builder.append("    \"moduleMetrics\": [\n");
        for (int index = 0; index < report.runtimeMetrics().moduleMetrics().size(); index++) {
            ModuleRuntimeMetrics moduleMetric = report.runtimeMetrics().moduleMetrics().get(index);
            builder.append("      {\n");
            builder.append("        \"moduleId\": \"").append(ReportWriterSupport.escapeJson(moduleMetric.moduleId())).append("\",\n");
            builder.append("        \"sourceFileCount\": ").append(moduleMetric.sourceFileCount()).append(",\n");
            builder.append("        \"analyzedFileCount\": ").append(moduleMetric.analyzedFileCount()).append(",\n");
            builder.append("        \"cachedFileCount\": ").append(moduleMetric.cachedFileCount()).append(",\n");
            builder.append("        \"parseProblemFileCount\": ").append(moduleMetric.parseProblemFileCount()).append(",\n");
            builder.append("        \"analysisMillis\": ").append(moduleMetric.analysisMillis()).append(",\n");
            builder.append("        \"analyzedMillis\": ").append(moduleMetric.analyzedMillis()).append(",\n");
            builder.append("        \"cachedMillis\": ").append(moduleMetric.cachedMillis()).append(",\n");
            builder.append("        \"cacheHitRatePercent\": ").append(moduleMetric.cacheHitRatePercent()).append('\n');
            builder.append("      }");
            if (index < report.runtimeMetrics().moduleMetrics().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("    ],\n");
        builder.append("    \"slowModules\": [\n");
        List<ModuleRuntimeMetrics> slowModules = report.runtimeMetrics().slowestModules(5);
        for (int index = 0; index < slowModules.size(); index++) {
            ModuleRuntimeMetrics moduleMetric = slowModules.get(index);
            builder.append("      {\n");
            builder.append("        \"moduleId\": \"").append(ReportWriterSupport.escapeJson(moduleMetric.moduleId())).append("\",\n");
            builder.append("        \"analysisMillis\": ").append(moduleMetric.analysisMillis()).append(",\n");
            builder.append("        \"analyzedMillis\": ").append(moduleMetric.analyzedMillis()).append(",\n");
            builder.append("        \"cachedMillis\": ").append(moduleMetric.cachedMillis()).append(",\n");
            builder.append("        \"sourceFileCount\": ").append(moduleMetric.sourceFileCount()).append(",\n");
            builder.append("        \"analyzedFileCount\": ").append(moduleMetric.analyzedFileCount()).append(",\n");
            builder.append("        \"cachedFileCount\": ").append(moduleMetric.cachedFileCount()).append(",\n");
            builder.append("        \"cacheHitRatePercent\": ").append(moduleMetric.cacheHitRatePercent()).append('\n');
            builder.append("      }");
            if (index < slowModules.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("    ]\n");
        builder.append("  },\n");
        builder.append("  \"rules\": [\n");
        for (int index = 0; index < report.rules().size(); index++) {
            RuleDescriptor rule = report.rules().get(index);
            builder.append("    {\n");
            builder.append("      \"id\": \"").append(ReportWriterSupport.escapeJson(rule.id())).append("\",\n");
            builder.append("      \"title\": \"").append(ReportWriterSupport.escapeJson(rule.title())).append("\",\n");
            builder.append("      \"description\": \"").append(ReportWriterSupport.escapeJson(rule.description())).append("\",\n");
            builder.append("      \"domain\": \"").append(rule.domain()).append("\",\n");
            builder.append("      \"defaultSeverity\": \"").append(rule.defaultSeverity()).append("\",\n");
            appendStringArray(builder, "appliesWhen", rule.appliesWhen(), 6);
            builder.append(",\n");
            appendStringArray(builder, "commonFalsePositiveBoundaries", rule.commonFalsePositiveBoundaries(), 6);
            builder.append(",\n");
            appendStringArray(builder, "recommendedFixes", rule.recommendedFixes(), 6);
            builder.append('\n');
            builder.append("    }");
            if (index < report.rules().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ],\n");
        ruleGuidanceJsonSupport.appendRuleGuidanceArray(builder, "ruleGuidance", RuleGuidanceSummaries.forLintReport(report), 2);
        builder.append(",\n");
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

    private void appendStringArray(StringBuilder builder, String fieldName, java.util.List<String> values, int indentSize) {
        ruleGuidanceJsonSupport.appendStringArray(builder, fieldName, values, indentSize);
    }

    private void appendRuleDomainSelection(StringBuilder builder, RuleDomainSelectionSummary summary) {
        builder.append("  \"ruleDomainSelection\": {\n");
        appendDomainArray(builder, "enabledDomains", summary.enabledDomains(), 4);
        builder.append(",\n");
        appendDomainArray(builder, "disabledDomains", summary.disabledDomains(), 4);
        builder.append(",\n");
        appendDomainArray(builder, "effectiveDomains", summary.effectiveDomains(), 4);
        builder.append(",\n");
        appendStringArray(builder, "enabledRuleIds", summary.enabledRuleIds(), 4);
        builder.append(",\n");
        appendStringArray(builder, "disabledRuleIds", summary.disabledRuleIds(), 4);
        builder.append(",\n");
        appendStringArray(builder, "effectiveRuleIds", summary.effectiveRuleIds(), 4);
        builder.append(",\n");
        appendRuleDomainBreakdown(builder, summary.effectiveRuleBreakdown(), 4);
        builder.append("\n  }");
    }

    private void appendDomainArray(StringBuilder builder, String fieldName, java.util.List<RuleDomain> values, int indentSize) {
        appendStringArray(builder, fieldName, values.stream().map(Enum::name).toList(), indentSize);
    }

    private void appendRuleDomainBreakdown(StringBuilder builder, java.util.List<RuleDomainRuleSummary> breakdown, int indentSize) {
        String indent = " ".repeat(indentSize);
        String childIndent = " ".repeat(indentSize + 2);
        builder.append(indent).append("\"effectiveRuleBreakdown\": [\n");
        for (int index = 0; index < breakdown.size(); index++) {
            RuleDomainRuleSummary summary = breakdown.get(index);
            builder.append(childIndent).append("{\n");
            builder.append(childIndent).append("  \"domain\": \"").append(summary.domain().name()).append("\",\n");
            builder.append(childIndent).append("  \"ruleCount\": ").append(summary.ruleCount()).append(",\n");
            appendStringArray(builder, "ruleIds", summary.ruleIds(), indentSize + 4);
            builder.append('\n');
            builder.append(childIndent).append("}");
            if (index < breakdown.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append(indent).append(']');
    }

    private void appendRuntimeSummary(StringBuilder builder, LintReport report, int indentSize) {
        String indent = " ".repeat(indentSize);
        String childIndent = " ".repeat(indentSize + 2);
        builder.append(indent).append("\"runtimeSummary\": {\n");
        builder.append(childIndent).append("\"incrementalCacheEnabled\": ").append(report.runtimeMetrics().incrementalCacheEnabled()).append(",\n");
        builder.append(childIndent).append("\"cacheScope\": \"").append(ReportWriterSupport.escapeJson(report.runtimeMetrics().cacheScope())).append("\",\n");
        builder.append(childIndent).append("\"totalElapsedMillis\": ").append(report.runtimeMetrics().totalElapsedMillis()).append(",\n");
        builder.append(childIndent).append("\"sourceFileCount\": ").append(report.runtimeMetrics().sourceFileCount()).append(",\n");
        builder.append(childIndent).append("\"analyzedFileCount\": ").append(report.runtimeMetrics().analyzedFileCount()).append(",\n");
        builder.append(childIndent).append("\"cachedFileCount\": ").append(report.runtimeMetrics().cachedFileCount()).append(",\n");
        builder.append(childIndent).append("\"parseProblemFileCount\": ").append(report.runtimeMetrics().parseProblemFileCount()).append(",\n");
        builder.append(childIndent).append("\"cacheHitRatePercent\": ").append(report.runtimeMetrics().cacheHitRatePercent()).append(",\n");
        appendStringArray(builder, "cacheMissReasons", report.runtimeMetrics().cacheMissReasons(), indentSize + 2);
        builder.append(",\n");
        appendLightweightSlowModules(builder, report.runtimeMetrics().slowestModules(3), indentSize + 2);
        builder.append("\n").append(indent).append("}");
    }

    private void appendLightweightSlowModules(StringBuilder builder, java.util.List<ModuleRuntimeMetrics> slowModules, int indentSize) {
        String indent = " ".repeat(indentSize);
        String childIndent = " ".repeat(indentSize + 2);
        builder.append(indent).append("\"slowModules\": [\n");
        for (int index = 0; index < slowModules.size(); index++) {
            ModuleRuntimeMetrics moduleMetric = slowModules.get(index);
            builder.append(childIndent).append("{\n");
            builder.append(childIndent).append("  \"moduleId\": \"").append(ReportWriterSupport.escapeJson(moduleMetric.moduleId())).append("\",\n");
            builder.append(childIndent).append("  \"analyzedMillis\": ").append(moduleMetric.analyzedMillis()).append(",\n");
            builder.append(childIndent).append("  \"cacheHitRatePercent\": ").append(moduleMetric.cacheHitRatePercent()).append("\n");
            builder.append(childIndent).append("}");
            if (index < slowModules.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append(indent).append(']');
    }
}
