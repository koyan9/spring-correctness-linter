/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;

public record LintAnalysisResult(
        LintReport report,
        List<LintIssue> baselineCandidates,
        BaselineDiffReport baselineDiffReport
) {

    public LintAnalysisResult {
        baselineCandidates = List.copyOf(baselineCandidates);
    }
}