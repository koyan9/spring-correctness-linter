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
        Map<String, String> baselineModules
) {

    public BaselineDiffReport(
            List<LintIssue> newIssues,
            Set<BaselineEntry> matchedEntries,
            Set<BaselineEntry> staleEntries
    ) {
        this(newIssues, matchedEntries, staleEntries, List.of(), Map.of(), Map.of());
    }

    public BaselineDiffReport {
        newIssues = List.copyOf(newIssues);
        matchedEntries = Set.copyOf(matchedEntries);
        staleEntries = Set.copyOf(staleEntries);
        moduleSummaries = List.copyOf(moduleSummaries);
        fileModules = Map.copyOf(fileModules);
        baselineModules = Map.copyOf(baselineModules);
    }

    public String moduleForIssue(LintIssue issue) {
        return fileModules.getOrDefault(issue.file().toAbsolutePath().normalize().toString(), ".");
    }

    public String moduleForEntry(BaselineEntry entry) {
        return baselineModules.getOrDefault(entry.relativePath(), ".");
    }
}
