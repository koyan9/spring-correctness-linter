/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;

public record RuleGuidanceSummary(
        String ruleId,
        String title,
        RuleDomain domain,
        LintSeverity defaultSeverity,
        String description,
        long findingCount,
        long staleEntryCount,
        List<String> appliesWhen,
        List<String> commonFalsePositiveBoundaries,
        List<String> recommendedFixes
) {

    public RuleGuidanceSummary {
        domain = domain == null ? RuleDomain.GENERAL : domain;
        appliesWhen = List.copyOf(appliesWhen);
        commonFalsePositiveBoundaries = List.copyOf(commonFalsePositiveBoundaries);
        recommendedFixes = List.copyOf(recommendedFixes);
    }
}
