/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record LintReport(
        Path projectRoot,
        Path sourceDirectory,
        List<Path> sourceDirectories,
        Instant generatedAt,
        List<RuleDescriptor> rules,
        List<LintIssue> issues,
        long suppressedIssueCount,
        long baselineMatchedIssueCount,
        long staleBaselineEntryCount,
        long cachedFileCount,
        List<ModuleSummary> moduleSummaries,
        Map<String, String> fileModules,
        List<SourceParseProblem> parseProblems
) {

    public LintReport(
            Path projectRoot,
            Path sourceDirectory,
            Instant generatedAt,
            List<RuleDescriptor> rules,
            List<LintIssue> issues,
            long suppressedIssueCount,
            long baselineMatchedIssueCount,
            long staleBaselineEntryCount
    ) {
        this(
                projectRoot,
                sourceDirectory,
                List.of(sourceDirectory),
                generatedAt,
                rules,
                issues,
                suppressedIssueCount,
                baselineMatchedIssueCount,
                staleBaselineEntryCount,
                0,
                List.of(),
                Map.of(),
                List.of()
        );
    }

    public LintReport(
            Path projectRoot,
            Path sourceDirectory,
            Instant generatedAt,
            List<RuleDescriptor> rules,
            List<LintIssue> issues,
            long suppressedIssueCount,
            long baselineMatchedIssueCount,
            long staleBaselineEntryCount,
            List<SourceParseProblem> parseProblems
    ) {
        this(
                projectRoot,
                sourceDirectory,
                List.of(sourceDirectory),
                generatedAt,
                rules,
                issues,
                suppressedIssueCount,
                baselineMatchedIssueCount,
                staleBaselineEntryCount,
                0,
                List.of(),
                Map.of(),
                parseProblems
        );
    }

    public LintReport(
            Path projectRoot,
            Path sourceDirectory,
            List<Path> sourceDirectories,
            Instant generatedAt,
            List<RuleDescriptor> rules,
            List<LintIssue> issues,
            long suppressedIssueCount,
            long baselineMatchedIssueCount,
            long staleBaselineEntryCount
    ) {
        this(
                projectRoot,
                sourceDirectory,
                sourceDirectories,
                generatedAt,
                rules,
                issues,
                suppressedIssueCount,
                baselineMatchedIssueCount,
                staleBaselineEntryCount,
                0,
                List.of(),
                Map.of(),
                List.of()
        );
    }

    public LintReport(
            Path projectRoot,
            Path sourceDirectory,
            List<Path> sourceDirectories,
            Instant generatedAt,
            List<RuleDescriptor> rules,
            List<LintIssue> issues,
            long suppressedIssueCount,
            long baselineMatchedIssueCount,
            long staleBaselineEntryCount,
            List<SourceParseProblem> parseProblems
    ) {
        this(
                projectRoot,
                sourceDirectory,
                sourceDirectories,
                generatedAt,
                rules,
                issues,
                suppressedIssueCount,
                baselineMatchedIssueCount,
                staleBaselineEntryCount,
                0,
                List.of(),
                Map.of(),
                parseProblems
        );
    }

    public LintReport {
        sourceDirectories = List.copyOf(sourceDirectories);
        rules = List.copyOf(rules);
        moduleSummaries = moduleSummaries.stream()
                .sorted(Comparator.comparing(ModuleSummary::moduleId))
                .toList();
        fileModules = Map.copyOf(fileModules);
        issues = issues.stream()
                .sorted(Comparator.comparing((LintIssue issue) -> issue.file().toString())
                        .thenComparingInt(LintIssue::line)
                        .thenComparing(LintIssue::ruleId))
                .toList();
        parseProblems = parseProblems.stream()
                .sorted(Comparator.comparing((SourceParseProblem problem) -> problem.file().toString()))
                .toList();
    }

    public long issueCount() {
        return issues.size();
    }

    public long parseProblemFileCount() {
        return parseProblems.size();
    }

    public long sourceDirectoryCount() {
        return sourceDirectories.size();
    }

    public String moduleFor(Path file) {
        return fileModules.getOrDefault(file.toAbsolutePath().normalize().toString(), ".");
    }

    public Map<LintSeverity, Long> severityCounts() {
        return issues.stream().collect(Collectors.groupingBy(LintIssue::severity, Collectors.counting()));
    }
}
