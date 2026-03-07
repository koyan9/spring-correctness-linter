/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.LintSeverity;
import io.github.koyan9.linter.core.SourceUnit;

import java.util.Set;

public abstract class AbstractSpringRule implements LintRule {

    protected LintIssue issue(SourceUnit sourceUnit, int line, String message) {
        return new LintIssue(id(), severity(), message, sourceUnit.path(), line);
    }

    protected boolean hasSecurityAnnotation(Set<String> annotations) {
        return annotations.contains("PreAuthorize")
                || annotations.contains("Secured")
                || annotations.contains("RolesAllowed")
                || annotations.contains("DenyAll")
                || annotations.contains("PermitAll");
    }

    @Override
    public LintSeverity severity() {
        return LintSeverity.WARNING;
    }
}