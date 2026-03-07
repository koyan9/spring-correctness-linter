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
import java.util.Set;

public final class CacheAnnotationCombinationRiskRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_CACHE_COMBINATION_RISK";
    }

    @Override
    public String title() {
        return "Review risky cache annotation combinations";
    }

    @Override
    public String description() {
        return "Combining multiple cache annotations on the same method can make cache semantics hard to reason about and may hide stale-data bugs.";
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        sourceUnit.compilationUnit().ifPresent(compilationUnit -> {
            for (MethodDeclaration method : compilationUnit.findAll(MethodDeclaration.class)) {
                Set<String> annotations = JavaSourceInspector.annotationNames(method);
                int count = 0;
                if (annotations.contains("Cacheable")) {
                    count++;
                }
                if (annotations.contains("CachePut")) {
                    count++;
                }
                if (annotations.contains("CacheEvict")) {
                    count++;
                }
                if (count >= 2) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Method '" + method.getNameAsString() + "' combines multiple cache annotations; review whether cache population and eviction semantics are explicit and safe."));
                }
            }
        });
        return issues;
    }
}