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

public final class TransactionalPrivateMethodRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_TX_PRIVATE_METHOD";
    }

    @Override
    public String title() {
        return "Avoid @Transactional on private methods";
    }

    @Override
    public String description() {
        return "Private @Transactional methods are not proxied by Spring AOP and transaction advice will not run.";
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        sourceUnit.compilationUnit().ifPresent(compilationUnit -> {
            for (MethodDeclaration method : compilationUnit.findAll(MethodDeclaration.class)) {
                if (JavaSourceInspector.hasAnnotation(method, "Transactional") && method.isPrivate()) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Transactional method '" + method.getNameAsString() + "' is private and will not be advised by Spring proxies."));
                }
            }
        });
        return issues;
    }
}