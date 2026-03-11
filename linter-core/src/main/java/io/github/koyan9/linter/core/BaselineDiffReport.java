/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record BaselineDiffReport(
        List<LintIssue> newIssues,
        Set<BaselineEntry> matchedEntries,
        Set<BaselineEntry> staleEntries,
        java.util.List<BaselineDiffModuleSummary> moduleSummaries,
        Map<String, String> fileModules,
        Map<String, String> baselineModules,
        List<RuleDescriptor> rules,
        RuleDomainSelectionSummary ruleDomainSelection
) {

    public BaselineDiffReport(
            List<LintIssue> newIssues,
            Set<BaselineEntry> matchedEntries,
            Set<BaselineEntry> staleEntries
    ) {
        this(newIssues, matchedEntries, staleEntries, List.of(), Map.of(), Map.of(), List.of(), RuleDomainSelectionSummary.empty());
    }

    public BaselineDiffReport(
            List<LintIssue> newIssues,
            Set<BaselineEntry> matchedEntries,
            Set<BaselineEntry> staleEntries,
            java.util.List<BaselineDiffModuleSummary> moduleSummaries,
            Map<String, String> fileModules,
            Map<String, String> baselineModules
    ) {
        this(newIssues, matchedEntries, staleEntries, moduleSummaries, fileModules, baselineModules, List.of(), RuleDomainSelectionSummary.empty());
    }

    public BaselineDiffReport(
            List<LintIssue> newIssues,
            Set<BaselineEntry> matchedEntries,
            Set<BaselineEntry> staleEntries,
            java.util.List<BaselineDiffModuleSummary> moduleSummaries,
            Map<String, String> fileModules,
            Map<String, String> baselineModules,
            List<RuleDescriptor> rules
    ) {
        this(newIssues, matchedEntries, staleEntries, moduleSummaries, fileModules, baselineModules, rules, RuleDomainSelectionSummary.empty());
    }

    public BaselineDiffReport {
        newIssues = List.copyOf(newIssues);
        matchedEntries = Set.copyOf(matchedEntries);
        staleEntries = Set.copyOf(staleEntries);
        moduleSummaries = List.copyOf(moduleSummaries);
        fileModules = Map.copyOf(fileModules);
        baselineModules = Map.copyOf(baselineModules);
        rules = List.copyOf(rules);
        ruleDomainSelection = ruleDomainSelection == null ? RuleDomainSelectionSummary.fromRules(rules) : ruleDomainSelection;
    }

    public String moduleForIssue(LintIssue issue) {
        return fileModules.getOrDefault(issue.file().toAbsolutePath().normalize().toString(), ".");
    }

    public String moduleForEntry(BaselineEntry entry) {
        return baselineModules.getOrDefault(entry.relativePath(), ".");
    }
}
