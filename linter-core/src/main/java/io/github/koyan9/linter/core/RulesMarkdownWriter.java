/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class RulesMarkdownWriter {

    private static final List<RecommendedBundle> RECOMMENDED_BUNDLES = List.of(
            new RecommendedBundle(
                    "CI Starter",
                    "Broad runtime-safety coverage for common service and API risks.",
                    List.of(RuleDomain.ASYNC, RuleDomain.TRANSACTION, RuleDomain.WEB)
            ),
            new RecommendedBundle(
                    "Transaction Focus",
                    "Prioritize transaction boundaries, propagation, and event timing semantics.",
                    List.of(RuleDomain.TRANSACTION, RuleDomain.EVENTS)
            ),
            new RecommendedBundle(
                    "Web/API Focus",
                    "Review endpoint exposure, controller drift, and security intent close to API entrypoints.",
                    List.of(RuleDomain.WEB)
            ),
            new RecommendedBundle(
                    "Cache Focus",
                    "Review cache-key choices and multi-annotation cache behavior before wider rollout.",
                    List.of(RuleDomain.CACHE)
            )
    );

    String write(Iterable<RuleDescriptor> rules) {
        List<RuleDescriptor> descriptors = new java.util.ArrayList<>();
        for (RuleDescriptor rule : rules) {
            descriptors.add(rule);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# Generated Rule Reference\n\n");
        builder.append("This file is generated from the current rule registry. Use it as the default rule index for CLI, Maven, and CI integration.\n\n");
        builder.append("## Usage\n\n");
        builder.append("- Disable rules: `-Dspring.correctness.linter.disabledRules=RULE_A,RULE_B`\n");
        builder.append("- Enable only selected rules: `-Dspring.correctness.linter.enabledRules=RULE_A,RULE_B`\n");
        builder.append("- Enable rule domains: `-Dspring.correctness.linter.enabledRuleDomains=TRANSACTION,CACHE`\n");
        builder.append("- Disable rule domains: `-Dspring.correctness.linter.disabledRuleDomains=WEB`\n");
        builder.append("- Override severities: `-Dspring.correctness.linter.severityOverrides=RULE_A=ERROR,RULE_B=INFO`\n");
        builder.append("- Centralized security: `-Dspring.correctness.linter.assumeCentralizedSecurity=true`\n");
        builder.append("- Custom security annotations: `-Dspring.correctness.linter.securityAnnotations=CustomSecured,TeamSecure`\n");
        builder.append("- Allow default cache keys: `-Dspring.correctness.linter.cacheDefaultKeyCacheNames=users,orders`\n");
        builder.append("- Suppress one finding: `// spring-correctness-linter:disable-next-line RULE_ID reason: explanation`\n\n");
        appendRecommendedBundles(builder, descriptors);
        appendRuleDomains(builder, descriptors);
        builder.append("## Rule Index\n\n");
        builder.append("| Rule ID | Default Severity | Title |\n");
        builder.append("| --- | --- | --- |\n");
        for (RuleDescriptor rule : descriptors) {
            builder.append("| `").append(rule.id()).append("` | `")
                    .append(rule.defaultSeverity()).append("` | ")
                    .append(rule.title()).append(" |\n");
        }
        builder.append("\n## Rule Details\n\n");
        for (RuleDescriptor rule : descriptors) {
            builder.append("### `").append(rule.id()).append("`\n");
            builder.append("- Domain: `").append(rule.domain()).append("`\n");
            builder.append("- Title: ").append(rule.title()).append("\n");
            builder.append("- Default severity: `").append(rule.defaultSeverity()).append("`\n");
            builder.append("- Description: ").append(rule.description()).append("\n");
            builder.append("- Disable: `-Dspring.correctness.linter.disabledRules=").append(rule.id()).append("`\n");
            builder.append("- Run only this rule: `-Dspring.correctness.linter.enabledRules=").append(rule.id()).append("`\n");
            builder.append("- Override severity: `-Dspring.correctness.linter.severityOverrides=").append(rule.id()).append("=ERROR`\n");
            builder.append("- Suppress next line: `// spring-correctness-linter:disable-next-line ").append(rule.id()).append(" reason: explain why`\n");
            builder.append("- Suppress next method: `// spring-correctness-linter:disable-next-method ").append(rule.id()).append(" reason: explain why`\n");
            appendSection(builder, "Applies when", rule.appliesWhen(), "- No additional applicability notes.");
            appendSection(builder, "Common false-positive boundaries", rule.commonFalsePositiveBoundaries(), "- No common false-positive boundary notes are currently documented.");
            appendSection(builder, "Recommended fixes", rule.recommendedFixes(), "- No rule-specific remediation guidance is currently documented.");
            builder.append('\n');
        }
        return builder.toString();
    }

    private void appendRuleDomains(StringBuilder builder, List<RuleDescriptor> descriptors) {
        Map<RuleDomain, List<RuleDescriptor>> byDomain = new LinkedHashMap<>();
        for (RuleDescriptor descriptor : descriptors) {
            byDomain.computeIfAbsent(descriptor.domain(), ignored -> new java.util.ArrayList<>()).add(descriptor);
        }

        builder.append("## Rule Domains\n\n");
        for (Map.Entry<RuleDomain, List<RuleDescriptor>> entry : byDomain.entrySet()) {
            builder.append("### ").append(entry.getKey().displayName()).append("\n");
            for (RuleDescriptor descriptor : entry.getValue()) {
                builder.append("- `").append(descriptor.id()).append("`: ").append(descriptor.title()).append("\n");
            }
            builder.append('\n');
        }
    }

    private void appendRecommendedBundles(StringBuilder builder, List<RuleDescriptor> descriptors) {
        builder.append("## Recommended Bundles\n\n");
        Set<RuleDomain> availableDomains = descriptors.stream()
                .map(RuleDescriptor::domain)
                .collect(Collectors.toSet());

        boolean wroteBundle = false;
        for (RecommendedBundle bundle : RECOMMENDED_BUNDLES) {
            if (!availableDomains.containsAll(bundle.domains())) {
                continue;
            }
            List<RuleDescriptor> includedRules = descriptors.stream()
                    .filter(descriptor -> bundle.domains().contains(descriptor.domain()))
                    .toList();
            if (includedRules.isEmpty()) {
                continue;
            }

            wroteBundle = true;
            builder.append("### `").append(bundle.name()).append("`\n");
            builder.append("- Purpose: ").append(bundle.description()).append("\n");
            builder.append("- Domains: ")
                    .append(bundle.domains().stream().map(domain -> "`" + domain.name() + "`").collect(Collectors.joining(", ")))
                    .append("\n");
            builder.append("- Suggested property: `-Dspring.correctness.linter.enabledRuleDomains=")
                    .append(bundle.domains().stream().map(Enum::name).collect(Collectors.joining(",")))
                    .append("`\n");
            builder.append("- Included rules: ")
                    .append(includedRules.stream().map(rule -> "`" + rule.id() + "`").collect(Collectors.joining(", ")))
                    .append("\n\n");
        }

        if (!wroteBundle) {
            builder.append("No recommended bundles are available for the current rule set.\n\n");
        }
    }

    private void appendSection(StringBuilder builder, String title, java.util.List<String> items, String emptyMessage) {
        builder.append("\n#### ").append(title).append("\n");
        if (items.isEmpty()) {
            builder.append(emptyMessage).append("\n");
            return;
        }
        for (String item : items) {
            builder.append("- ").append(item).append("\n");
        }
    }

    private record RecommendedBundle(String name, String description, List<RuleDomain> domains) {

        private RecommendedBundle {
            domains = List.copyOf(domains);
        }
    }
}
