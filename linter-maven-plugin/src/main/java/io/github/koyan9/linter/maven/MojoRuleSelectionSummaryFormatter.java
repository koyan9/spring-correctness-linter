/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.RuleDomainSelectionSummary;

import java.util.List;
import java.util.stream.Collectors;

final class MojoRuleSelectionSummaryFormatter {

    String format(RuleDomainSelectionSummary summary) {
        StringBuilder builder = new StringBuilder("spring-correctness-linter selection: ");
        appendConfiguredDomains(builder, "enabled domains", summary.enabledDomains());
        appendConfiguredDomains(builder, "disabled domains", summary.disabledDomains());
        appendConfiguredRules(builder, "enabled rules", summary.enabledRuleIds());
        appendConfiguredRules(builder, "disabled rules", summary.disabledRuleIds());
        appendEffectiveDomains(builder, summary.effectiveDomains());
        appendEffectiveRules(builder, summary.effectiveRuleIds());
        return builder.toString();
    }

    private void appendConfiguredDomains(StringBuilder builder, String label, List<RuleDomain> domains) {
        if (domains.isEmpty()) {
            return;
        }
        appendSeparator(builder);
        builder.append(label)
                .append('=')
                .append(domains.stream().map(Enum::name).collect(Collectors.joining(",")));
    }

    private void appendConfiguredRules(StringBuilder builder, String label, List<String> ruleIds) {
        if (ruleIds.isEmpty()) {
            return;
        }
        appendSeparator(builder);
        builder.append(label)
                .append('=')
                .append(preview(ruleIds));
    }

    private void appendEffectiveDomains(StringBuilder builder, List<RuleDomain> domains) {
        appendSeparator(builder);
        builder.append("effective domains=")
                .append(domains.isEmpty()
                        ? "none"
                        : domains.stream().map(Enum::name).collect(Collectors.joining(",")));
    }

    private void appendEffectiveRules(StringBuilder builder, List<String> ruleIds) {
        appendSeparator(builder);
        builder.append("effective rules=")
                .append(ruleIds.size())
                .append(' ')
                .append(preview(ruleIds));
    }

    private String preview(List<String> values) {
        if (values.isEmpty()) {
            return "[]";
        }
        List<String> preview = values.size() > 5 ? values.subList(0, 5) : values;
        String suffix = values.size() > 5 ? ",..." : "";
        return "[" + String.join(",", preview) + suffix + "]";
    }

    private void appendSeparator(StringBuilder builder) {
        if (!builder.toString().endsWith(": ")) {
            builder.append("; ");
        }
    }
}
