/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;

import java.util.ArrayList;
import java.util.List;

public final class CacheableWithoutKeyRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_CACHEABLE_KEY";
    }

    @Override
    public String title() {
        return "Declare cache keys explicitly";
    }

    @Override
    public String description() {
        return "Explicit keys reduce accidental cache collisions when method signatures evolve.";
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (JavaSourceInspector.hasAnnotation(method, "Cacheable")
                    && !JavaSourceInspector.annotationDeclaresMember(method, "Cacheable", "key")) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Cacheable method '" + method.getNameAsString() + "' does not declare an explicit key."));
            }
        }
        return issues;
    }
}
