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
        Instant generatedAt,
        List<RuleDescriptor> rules,
        List<LintIssue> issues,
        long suppressedIssueCount,
        long baselineMatchedIssueCount,
        long staleBaselineEntryCount
) {

    public LintReport {
        rules = List.copyOf(rules);
        issues = issues.stream()
                .sorted(Comparator.comparing((LintIssue issue) -> issue.file().toString())
                        .thenComparingInt(LintIssue::line)
                        .thenComparing(LintIssue::ruleId))
                .toList();
    }

    public long issueCount() {
        return issues.size();
    }

    public Map<LintSeverity, Long> severityCounts() {
        return issues.stream().collect(Collectors.groupingBy(LintIssue::severity, Collectors.counting()));
    }
}