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

public final class AsyncFinalMethodRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_ASYNC_FINAL_METHOD";
    }

    @Override
    public String title() {
        return "Avoid final @Async methods";
    }

    @Override
    public String description() {
        return "Final @Async methods cannot be advised when class-based proxies are used.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.ASYNC;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method is asynchronous by `@Async` or a class-level async boundary and is also declared `final`.",
                "The application relies on proxy-based Spring AOP for async method interception."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Interface-based proxies or AspectJ weaving may avoid the impact of final methods in some projects.",
                "Async entrypoints outside Spring-managed beans may still be flagged even though the runtime symptom differs."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Remove the `final` modifier from async entrypoints when proxy-based interception is expected.",
                "Move the async boundary into a separate bean if the current method must remain final."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            TypeSemanticFacts typeFacts = facts.typeFacts(typeDeclaration);
            boolean classAsync = typeFacts.hasAsyncBoundary();
            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                MethodSemanticFacts methodFacts = facts.methodFacts(typeDeclaration, method);
                boolean async = methodFacts.hasAsyncBoundary() || (classAsync && method.isPublic());
                if (async && method.isFinal()) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Async method '" + method.getNameAsString() + "' is final; class-based proxies cannot intercept final methods."));
                }
            }
        }
        return issues;
    }
}
