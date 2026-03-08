/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;

import java.util.ArrayList;
import java.util.List;

public final class ProfileOnControllerRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_PROFILE_CONTROLLER";
    }

    @Override
    public String title() {
        return "Avoid @Profile directly on controllers";
    }

    @Override
    public String description() {
        return "Hiding controllers by profile can create environment-specific API drift and unclear contract changes.";
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        collectIssues(sourceUnit, issues);
        return issues;
    }

    private void collectIssues(SourceUnit sourceUnit, List<LintIssue> issues) {
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            if (JavaSourceInspector.hasAnnotation(typeDeclaration, "Profile") && JavaSourceInspector.isController(typeDeclaration)) {
                issues.add(issue(
                        sourceUnit,
                        JavaSourceInspector.lineOf(typeDeclaration),
                        "Controller '" + typeDeclaration.getNameAsString() + "' is gated by @Profile; prefer routing, feature flags, or conditional delegation."
                ));
            }
        }
    }
}
