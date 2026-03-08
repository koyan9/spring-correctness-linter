/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

public record ModuleSummary(
        String moduleId,
        long sourceDirectoryCount,
        long sourceFileCount,
        long visibleIssueCount,
        long parseProblemFileCount,
        long cachedFileCount
) {
}
