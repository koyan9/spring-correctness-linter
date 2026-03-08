/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

public record BaselineDiffModuleSummary(
        String moduleId,
        long newIssueCount,
        long matchedBaselineCount,
        long staleBaselineCount
) {
}
