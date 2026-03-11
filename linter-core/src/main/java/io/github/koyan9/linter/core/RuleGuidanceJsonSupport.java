/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;

final class RuleGuidanceJsonSupport {

    void appendRuleGuidanceArray(StringBuilder builder, String fieldName, List<RuleGuidanceSummary> summaries, int indentSize) {
        String indent = " ".repeat(indentSize);
        String childIndent = " ".repeat(indentSize + 2);
        builder.append(indent)
                .append("\"")
                .append(fieldName)
                .append("\": [\n");
        for (int index = 0; index < summaries.size(); index++) {
            RuleGuidanceSummary summary = summaries.get(index);
            builder.append(childIndent).append("{\n");
            builder.append(childIndent).append("  \"ruleId\": \"").append(ReportWriterSupport.escapeJson(summary.ruleId())).append("\",\n");
            builder.append(childIndent).append("  \"title\": \"").append(ReportWriterSupport.escapeJson(summary.title())).append("\",\n");
            builder.append(childIndent).append("  \"defaultSeverity\": \"").append(summary.defaultSeverity()).append("\",\n");
            builder.append(childIndent).append("  \"domain\": \"").append(summary.domain()).append("\",\n");
            builder.append(childIndent).append("  \"description\": \"").append(ReportWriterSupport.escapeJson(summary.description())).append("\",\n");
            builder.append(childIndent).append("  \"findingCount\": ").append(summary.findingCount()).append(",\n");
            builder.append(childIndent).append("  \"staleEntryCount\": ").append(summary.staleEntryCount()).append(",\n");
            appendStringArray(builder, "appliesWhen", summary.appliesWhen(), indentSize + 4);
            builder.append(",\n");
            appendStringArray(builder, "commonFalsePositiveBoundaries", summary.commonFalsePositiveBoundaries(), indentSize + 4);
            builder.append(",\n");
            appendStringArray(builder, "recommendedFixes", summary.recommendedFixes(), indentSize + 4);
            builder.append('\n');
            builder.append(childIndent).append("}");
            if (index < summaries.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append(indent).append(']');
    }

    void appendStringArray(StringBuilder builder, String fieldName, List<String> values, int indentSize) {
        String indent = " ".repeat(indentSize);
        String childIndent = " ".repeat(indentSize + 2);
        builder.append(indent)
                .append("\"")
                .append(fieldName)
                .append("\": [\n");
        for (int index = 0; index < values.size(); index++) {
            builder.append(childIndent)
                    .append("\"")
                    .append(ReportWriterSupport.escapeJson(values.get(index)))
                    .append("\"");
            if (index < values.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append(indent).append(']');
    }
}
