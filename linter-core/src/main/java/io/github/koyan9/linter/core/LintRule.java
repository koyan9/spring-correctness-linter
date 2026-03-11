/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;

public interface LintRule {

    String id();

    String title();

    String description();

    LintSeverity severity();

    default Class<?> implementationClass() {
        return getClass();
    }

    default String implementationIdentity() {
        return implementationClass().getName();
    }

    default RuleDomain domain() {
        return RuleDomain.GENERAL;
    }

    default List<String> appliesWhen() {
        return List.of();
    }

    default List<String> commonFalsePositiveBoundaries() {
        return List.of();
    }

    default List<String> recommendedFixes() {
        return List.of();
    }

    List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context);
}
