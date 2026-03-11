/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record RuleDomainSelectionSummary(
        List<RuleDomain> enabledDomains,
        List<RuleDomain> disabledDomains,
        List<RuleDomain> effectiveDomains,
        List<String> enabledRuleIds,
        List<String> disabledRuleIds,
        List<String> effectiveRuleIds,
        List<RuleDomainRuleSummary> effectiveRuleBreakdown
) {

    public RuleDomainSelectionSummary {
        enabledDomains = normalize(enabledDomains);
        disabledDomains = normalize(disabledDomains);
        effectiveDomains = normalize(effectiveDomains);
        enabledRuleIds = normalizeRuleIds(enabledRuleIds);
        disabledRuleIds = normalizeRuleIds(disabledRuleIds);
        effectiveRuleIds = normalizeRuleIds(effectiveRuleIds);
        effectiveRuleBreakdown = effectiveRuleBreakdown == null
                ? List.of()
                : effectiveRuleBreakdown.stream()
                        .sorted(Comparator.comparing(summary -> summary.domain().name()))
                        .toList();
    }

    public static RuleDomainSelectionSummary empty() {
        return new RuleDomainSelectionSummary(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public static RuleDomainSelectionSummary fromRules(List<RuleDescriptor> rules) {
        return new RuleDomainSelectionSummary(
                List.of(),
                List.of(),
                rules.stream().map(RuleDescriptor::domain).toList(),
                List.of(),
                List.of(),
                rules.stream().map(RuleDescriptor::id).toList(),
                buildRuleBreakdown(rules)
        );
    }

    public static RuleDomainSelectionSummary fromConfiguredAndRules(
            Set<RuleDomain> enabledDomains,
            Set<RuleDomain> disabledDomains,
            Set<String> enabledRuleIds,
            Set<String> disabledRuleIds,
            List<RuleDescriptor> rules
    ) {
        return new RuleDomainSelectionSummary(
                enabledDomains == null ? List.of() : List.copyOf(enabledDomains),
                disabledDomains == null ? List.of() : List.copyOf(disabledDomains),
                rules.stream().map(RuleDescriptor::domain).toList(),
                enabledRuleIds == null ? List.of() : List.copyOf(enabledRuleIds),
                disabledRuleIds == null ? List.of() : List.copyOf(disabledRuleIds),
                rules.stream().map(RuleDescriptor::id).toList(),
                buildRuleBreakdown(rules)
        );
    }

    private static List<RuleDomain> normalize(List<RuleDomain> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        return domains.stream()
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private static List<String> normalizeRuleIds(List<String> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return List.of();
        }
        return ruleIds.stream()
                .filter(ruleId -> ruleId != null && !ruleId.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private static List<RuleDomainRuleSummary> buildRuleBreakdown(List<RuleDescriptor> rules) {
        return rules.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        RuleDescriptor::domain,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(RuleDescriptor::id, java.util.stream.Collectors.toList())
                ))
                .entrySet().stream()
                .map(entry -> new RuleDomainRuleSummary(entry.getKey(), entry.getValue()))
                .toList();
    }
}
