/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuleSelection {

    private RuleSelection() {
    }

    public static List<LintRule> configure(
            List<LintRule> availableRules,
            Set<String> enabledRules,
            Set<String> disabledRules,
            Map<String, LintSeverity> severityOverrides
    ) {
        return configure(availableRules, enabledRules, disabledRules, Set.of(), Set.of(), severityOverrides);
    }

    public static List<LintRule> configure(
            List<LintRule> availableRules,
            Set<String> enabledRules,
            Set<String> disabledRules,
            Set<RuleDomain> enabledDomains,
            Set<RuleDomain> disabledDomains,
            Map<String, LintSeverity> severityOverrides
    ) {
        Map<String, LintRule> rulesById = new LinkedHashMap<>();
        for (LintRule rule : availableRules) {
            String ruleId = normalizeRuleId(rule.id());
            if (rulesById.put(ruleId, rule) != null) {
                throw new IllegalArgumentException("Duplicate rule id detected: " + ruleId);
            }
        }

        Set<String> normalizedEnabledRules = normalizeRuleIds(enabledRules);
        Set<String> normalizedDisabledRules = normalizeRuleIds(disabledRules);
        Set<RuleDomain> normalizedEnabledDomains = normalizeRuleDomains(enabledDomains);
        Set<RuleDomain> normalizedDisabledDomains = normalizeRuleDomains(disabledDomains);
        Map<String, LintSeverity> normalizedSeverityOverrides = normalizeSeverityOverrides(severityOverrides);

        validateRuleIds("enabledRules", normalizedEnabledRules, rulesById.keySet());
        validateRuleIds("disabledRules", normalizedDisabledRules, rulesById.keySet());
        validateRuleIds("severityOverrides", normalizedSeverityOverrides.keySet(), rulesById.keySet());

        Set<String> overlappingRules = new LinkedHashSet<>(normalizedEnabledRules);
        overlappingRules.retainAll(normalizedDisabledRules);
        if (!overlappingRules.isEmpty()) {
            throw new IllegalArgumentException("Rules cannot be both enabled and disabled: " + String.join(", ", overlappingRules));
        }

        Set<RuleDomain> overlappingDomains = new LinkedHashSet<>(normalizedEnabledDomains);
        overlappingDomains.retainAll(normalizedDisabledDomains);
        if (!overlappingDomains.isEmpty()) {
            throw new IllegalArgumentException("Rule domains cannot be both enabled and disabled: "
                    + overlappingDomains.stream().map(Enum::name).reduce((left, right) -> left + ", " + right).orElse(""));
        }

        Set<String> selectedRuleIds = normalizedEnabledRules.isEmpty() && normalizedEnabledDomains.isEmpty()
                ? new LinkedHashSet<>(rulesById.keySet())
                : new LinkedHashSet<>();

        if (!normalizedEnabledDomains.isEmpty()) {
            for (Map.Entry<String, LintRule> entry : rulesById.entrySet()) {
                if (normalizedEnabledDomains.contains(entry.getValue().domain())) {
                    selectedRuleIds.add(entry.getKey());
                }
            }
        }
        selectedRuleIds.addAll(normalizedEnabledRules);

        for (Map.Entry<String, LintRule> entry : rulesById.entrySet()) {
            if (normalizedDisabledDomains.contains(entry.getValue().domain())) {
                selectedRuleIds.remove(entry.getKey());
            }
        }
        selectedRuleIds.removeAll(normalizedDisabledRules);

        return rulesById.entrySet().stream()
                .filter(entry -> selectedRuleIds.contains(entry.getKey()))
                .map(entry -> applySeverityOverride(entry.getValue(), normalizedSeverityOverrides.get(entry.getKey())))
                .toList();
    }

    private static LintRule applySeverityOverride(LintRule rule, LintSeverity overrideSeverity) {
        if (overrideSeverity == null || overrideSeverity == rule.severity()) {
            return rule;
        }
        return new ConfiguredRule(rule, overrideSeverity);
    }

    private static Set<String> normalizeRuleIds(Set<String> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String ruleId : ruleIds) {
            if (ruleId != null && !ruleId.isBlank()) {
                normalized.add(normalizeRuleId(ruleId));
            }
        }
        return normalized;
    }

    private static Map<String, LintSeverity> normalizeSeverityOverrides(Map<String, LintSeverity> severityOverrides) {
        if (severityOverrides == null || severityOverrides.isEmpty()) {
            return Map.of();
        }

        Map<String, LintSeverity> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, LintSeverity> entry : severityOverrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            normalized.put(normalizeRuleId(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private static Set<RuleDomain> normalizeRuleDomains(Set<RuleDomain> ruleDomains) {
        if (ruleDomains == null || ruleDomains.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(ruleDomains);
    }

    private static void validateRuleIds(String fieldName, Set<String> requestedRuleIds, Set<String> availableRuleIds) {
        Set<String> unknownRules = new LinkedHashSet<>(requestedRuleIds);
        unknownRules.removeAll(availableRuleIds);
        if (!unknownRules.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown rule id(s) in " + fieldName + ": " + String.join(", ", unknownRules)
                            + ". Available rules: " + String.join(", ", availableRuleIds)
            );
        }
    }

    private static String normalizeRuleId(String ruleId) {
        return ruleId.trim().toUpperCase();
    }

    private static final class ConfiguredRule implements LintRule {

        private final LintRule delegate;
        private final LintSeverity severity;

        private ConfiguredRule(LintRule delegate, LintSeverity severity) {
            this.delegate = delegate;
            this.severity = severity;
        }

        @Override
        public String id() {
            return delegate.id();
        }

        @Override
        public String title() {
            return delegate.title();
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public RuleDomain domain() {
            return delegate.domain();
        }

        @Override
        public LintSeverity severity() {
            return severity;
        }

        @Override
        public Class<?> implementationClass() {
            return delegate.implementationClass();
        }

        @Override
        public String implementationIdentity() {
            return delegate.implementationIdentity() + ":" + severity;
        }

        @Override
        public List<String> appliesWhen() {
            return delegate.appliesWhen();
        }

        @Override
        public List<String> commonFalsePositiveBoundaries() {
            return delegate.commonFalsePositiveBoundaries();
        }

        @Override
        public List<String> recommendedFixes() {
            return delegate.recommendedFixes();
        }

        @Override
        public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
            return delegate.evaluate(sourceUnit, context).stream()
                    .map(issue -> new LintIssue(issue.ruleId(), severity, issue.message(), issue.file(), issue.line()))
                    .toList();
        }
    }
}
