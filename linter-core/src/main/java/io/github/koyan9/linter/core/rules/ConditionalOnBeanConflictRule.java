/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ConditionalOnBeanConflictRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_CONDITIONAL_BEAN_CONFLICT";
    }

    @Override
    public String title() {
        return "Detect conflicting conditional bean annotations";
    }

    @Override
    public String description() {
        return "Using @ConditionalOnBean and @ConditionalOnMissingBean together on the same type or bean method is usually contradictory.";
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        sourceUnit.compilationUnit().ifPresent(compilationUnit -> {
            for (TypeDeclaration<?> typeDeclaration : JavaSourceInspector.findTypeDeclarations(compilationUnit)) {
                check(sourceUnit, JavaSourceInspector.lineOf(typeDeclaration), typeDeclaration.getNameAsString(), JavaSourceInspector.annotationNames(typeDeclaration), issues);
                for (MethodDeclaration method : JavaSourceInspector.findMethods(typeDeclaration)) {
                    check(sourceUnit, JavaSourceInspector.lineOf(method), method.getNameAsString(), JavaSourceInspector.annotationNames(method), issues);
                }
            }
        });
        return issues;
    }

    private void check(SourceUnit sourceUnit, int line, String targetName, Set<String> annotations, List<LintIssue> issues) {
        if (annotations.contains("ConditionalOnBean") && annotations.contains("ConditionalOnMissingBean")) {
            issues.add(issue(sourceUnit, line, "Element '" + targetName + "' declares both @ConditionalOnBean and @ConditionalOnMissingBean; review the condition semantics."));
        }
    }
}