/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class RuleGuidanceSummaries {

    private RuleGuidanceSummaries() {
    }

    static List<RuleGuidanceSummary> forLintReport(LintReport report) {
        Map<String, Long> findingCounts = report.issues().stream()
                .collect(Collectors.groupingBy(LintIssue::ruleId, Collectors.counting()));
        return build(report.rules(), findingCounts, Map.of(), rule -> findingCounts.getOrDefault(rule.id(), 0L) > 0);
    }

    static List<RuleGuidanceSummary> forBaselineDiffReport(BaselineDiffReport report) {
        Map<String, Long> findingCounts = report.newIssues().stream()
                .collect(Collectors.groupingBy(LintIssue::ruleId, Collectors.counting()));
        Map<String, Long> staleCounts = report.staleEntries().stream()
                .collect(Collectors.groupingBy(BaselineEntry::ruleId, Collectors.counting()));
        Set<String> relevantRuleIds = java.util.stream.Stream.concat(findingCounts.keySet().stream(), staleCounts.keySet().stream())
                .collect(Collectors.toSet());
        return build(report.rules(), findingCounts, staleCounts, rule -> relevantRuleIds.contains(rule.id()));
    }

    private static List<RuleGuidanceSummary> build(
            List<RuleDescriptor> rules,
            Map<String, Long> findingCounts,
            Map<String, Long> staleCounts,
            Predicate<RuleDescriptor> includeRule
    ) {
        return rules.stream()
                .filter(includeRule)
                .map(rule -> new RuleGuidanceSummary(
                        rule.id(),
                        rule.title(),
                        rule.domain(),
                        rule.defaultSeverity(),
                        rule.description(),
                        findingCounts.getOrDefault(rule.id(), 0L),
                        staleCounts.getOrDefault(rule.id(), 0L),
                        rule.appliesWhen(),
                        rule.commonFalsePositiveBoundaries(),
                        rule.recommendedFixes()
                ))
                .toList();
    }
}
