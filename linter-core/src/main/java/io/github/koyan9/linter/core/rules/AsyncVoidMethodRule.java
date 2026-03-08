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

public final class AsyncVoidMethodRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_ASYNC_VOID";
    }

    @Override
    public String title() {
        return "Avoid void @Async methods";
    }

    @Override
    public String description() {
        return "Asynchronous methods should return CompletableFuture or another handle so callers can observe failures.";
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (JavaSourceInspector.hasAnnotation(method, "Async") && method.getType().isVoidType()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Async method '" + method.getNameAsString() + "' returns void; prefer CompletableFuture or another observable type."));
            }
        }
        return issues;
    }
}
