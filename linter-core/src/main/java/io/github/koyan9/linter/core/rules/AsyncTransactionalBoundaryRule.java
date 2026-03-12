/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.MethodSemanticFacts;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;
import io.github.koyan9.linter.core.TypeSemanticFacts;

import java.util.ArrayList;
import java.util.List;

public final class AsyncTransactionalBoundaryRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_ASYNC_TRANSACTIONAL_BOUNDARY";
    }

    @Override
    public String title() {
        return "Review @Async transactional boundaries";
    }

    @Override
    public String description() {
        return "Combining @Async with transactional boundaries can hide execution order and transactional scope across threads.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.ASYNC;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method is async and also transactional by method annotation or class-level @Transactional.",
                "Async execution crosses threads where transaction context propagation can be surprising."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Some background workflows intentionally open transactions within async tasks and are correct when bounded carefully.",
                "The rule is advisory and focuses on making cross-thread transaction semantics explicit."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Keep async entrypoints thin and delegate transactional work into explicit services when possible.",
                "If async work must be transactional, document the transaction boundary and test thread/rollback behavior explicitly."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            TypeSemanticFacts typeFacts = facts.typeFacts(typeDeclaration);
            boolean classAsync = typeFacts.hasAsyncBoundary();
            boolean classTransactional = typeFacts.hasTransactionalBoundary();
            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                MethodSemanticFacts methodFacts = facts.methodFacts(typeDeclaration, method);
                boolean async = methodFacts.hasAsyncBoundary() || (classAsync && method.isPublic());
                boolean transactional = methodFacts.hasTransactionalBoundary() || (classTransactional && method.isPublic());
                if (async && transactional) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Method '" + method.getNameAsString() + "' combines @Async with transactional semantics; review cross-thread transaction scope and retry behavior."));
                }
            }
        }
        return issues;
    }
}
