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

public final class EventListenerTransactionalRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_EVENT_LISTENER_TRANSACTIONAL";
    }

    @Override
    public String title() {
        return "Review @EventListener transactional boundaries";
    }

    @Override
    public String description() {
        return "Methods that combine @EventListener with @Transactional can hide event timing assumptions; prefer @TransactionalEventListener when transaction phase matters.";
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        sourceUnit.compilationUnit().ifPresent(compilationUnit -> {
            for (TypeDeclaration<?> typeDeclaration : JavaSourceInspector.findTypeDeclarations(compilationUnit)) {
                boolean classTransactional = JavaSourceInspector.hasAnnotation(typeDeclaration, "Transactional");
                for (MethodDeclaration method : JavaSourceInspector.findMethods(typeDeclaration)) {
                    Set<String> methodAnnotations = JavaSourceInspector.annotationNames(method);
                    if (methodAnnotations.contains("TransactionalEventListener")) {
                        continue;
                    }
                    if (methodAnnotations.contains("EventListener")
                            && (methodAnnotations.contains("Transactional") || classTransactional)) {
                        issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Event listener method '" + method.getNameAsString() + "' runs with @Transactional semantics; if transaction phase matters, prefer @TransactionalEventListener or document the boundary explicitly."));
                    }
                }
            }
        });
        return issues;
    }
}