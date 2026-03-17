/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;

final class RuleGovernanceJsonWriter {

    String write(LintReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"generatedAt\": \"").append(ReportWriterSupport.escapeJson(report.generatedAt().toString())).append("\",\n");
        builder.append("  \"ruleCount\": ").append(report.rules().size()).append(",\n");
        appendSelection(builder, report.ruleDomainSelection());
        appendRules(builder, report.rules());
        builder.append("}\n");
        return builder.toString();
    }

    private void appendSelection(StringBuilder builder, RuleDomainSelectionSummary selection) {
        builder.append("  \"selection\": {\n");
        appendArray(builder, "enabledDomains", selection.enabledDomains().stream().map(Enum::name).toList(), "    ", true);
        appendArray(builder, "disabledDomains", selection.disabledDomains().stream().map(Enum::name).toList(), "    ", true);
        appendArray(builder, "effectiveDomains", selection.effectiveDomains().stream().map(Enum::name).toList(), "    ", true);
        appendArray(builder, "enabledRuleIds", selection.enabledRuleIds(), "    ", true);
        appendArray(builder, "disabledRuleIds", selection.disabledRuleIds(), "    ", true);
        appendArray(builder, "effectiveRuleIds", selection.effectiveRuleIds(), "    ", false);
        builder.append("  },\n");
    }

    private void appendRules(StringBuilder builder, List<RuleDescriptor> rules) {
        builder.append("  \"rules\": [\n");
        for (int index = 0; index < rules.size(); index++) {
            RuleDescriptor rule = rules.get(index);
            builder.append("    {\n");
            builder.append("      \"ruleId\": \"").append(ReportWriterSupport.escapeJson(rule.id())).append("\",\n");
            builder.append("      \"title\": \"").append(ReportWriterSupport.escapeJson(rule.title())).append("\",\n");
            builder.append("      \"domain\": \"").append(rule.domain().name()).append("\",\n");
            builder.append("      \"severity\": \"").append(rule.defaultSeverity().name()).append("\"\n");
            builder.append("    }");
            if (index < rules.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append("  ]\n");
    }

    private void appendArray(StringBuilder builder, String key, List<String> values, String indent, boolean trailingComma) {
        builder.append(indent).append("\"").append(key).append("\": [");
        for (int index = 0; index < values.size(); index++) {
            builder.append("\"").append(ReportWriterSupport.escapeJson(values.get(index))).append("\"");
            if (index < values.size() - 1) {
                builder.append(", ");
            }
        }
        builder.append("]");
        if (trailingComma) {
            builder.append(",");
        }
        builder.append("\n");
    }
}
