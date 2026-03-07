/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;
import java.util.Set;

public record BaselineDiffReport(
        List<LintIssue> newIssues,
        Set<BaselineEntry> matchedEntries,
        Set<BaselineEntry> staleEntries
) {

    public BaselineDiffReport {
        newIssues = List.copyOf(newIssues);
        matchedEntries = Set.copyOf(matchedEntries);
        staleEntries = Set.copyOf(staleEntries);
    }
}