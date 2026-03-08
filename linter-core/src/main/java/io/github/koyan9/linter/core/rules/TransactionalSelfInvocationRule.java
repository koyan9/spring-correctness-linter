/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class TransactionalSelfInvocationRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_TX_SELF_INVOCATION";
    }

    @Override
    public String title() {
        return "Detect transactional self invocation";
    }

    @Override
    public String description() {
        return "Calling a @Transactional method through this.method() bypasses Spring proxies.";
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        collectIssues(sourceUnit, issues);
        return issues;
    }

    private void collectIssues(SourceUnit sourceUnit, List<LintIssue> issues) {
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            Set<String> transactionalMethods = sourceUnit.structure().methodsOf(typeDeclaration).stream()
                    .filter(method -> JavaSourceInspector.hasAnnotation(method, "Transactional"))
                    .map(MethodDeclaration::getNameAsString)
                    .collect(Collectors.toSet());

            if (transactionalMethods.isEmpty()) {
                continue;
            }

            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                for (MethodCallExpr methodCall : method.findAll(MethodCallExpr.class)) {
                    if (methodCall.getScope().filter(ThisExpr.class::isInstance).isPresent()
                            && transactionalMethods.contains(methodCall.getNameAsString())) {
                        issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(methodCall), "Method '" + methodCall.getNameAsString() + "' is @Transactional and invoked via self-call; proxy advice will not run."));
                    }
                }
            }
        }
    }
}
