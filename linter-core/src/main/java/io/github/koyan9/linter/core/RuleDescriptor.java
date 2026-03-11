/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

public record RuleDescriptor(
        String id,
        String title,
        String description,
        RuleDomain domain,
        LintSeverity defaultSeverity,
        java.util.List<String> appliesWhen,
        java.util.List<String> commonFalsePositiveBoundaries,
        java.util.List<String> recommendedFixes
) {

    public RuleDescriptor(String id, String title, String description, LintSeverity defaultSeverity) {
        this(id, title, description, RuleDomain.GENERAL, defaultSeverity, java.util.List.of(), java.util.List.of(), java.util.List.of());
    }

    public RuleDescriptor(String id, String title, String description, RuleDomain domain, LintSeverity defaultSeverity) {
        this(id, title, description, domain, defaultSeverity, java.util.List.of(), java.util.List.of(), java.util.List.of());
    }

    public RuleDescriptor {
        domain = domain == null ? RuleDomain.GENERAL : domain;
        appliesWhen = java.util.List.copyOf(appliesWhen);
        commonFalsePositiveBoundaries = java.util.List.copyOf(commonFalsePositiveBoundaries);
        recommendedFixes = java.util.List.copyOf(recommendedFixes);
    }
}
