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

public final class AsyncUnsupportedReturnTypeRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_ASYNC_UNSUPPORTED_RETURN_TYPE";
    }

    @Override
    public String title() {
        return "Use supported @Async return types";
    }

    @Override
    public String description() {
        return "Spring @Async methods should return void or a Future-compatible type that Spring async proxies can adapt.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.ASYNC;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method is asynchronous by `@Async` or a class-level async boundary and returns neither `void` nor a recognized Future-compatible type.",
                "The application relies on Spring async proxies rather than manual executor submission."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Custom async adapters or uncommon Future subtypes may still be valid even though this rule only recognizes standard, low-noise Spring async return handles without a full symbol solver.",
                "Framework callback methods may constrain the return type even when asynchronous execution is still intentional."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Return `Future`, `CompletableFuture`, `ListenableFuture`, or `void` from async entrypoints.",
                "If a synchronous return type is required, remove `@Async` and move the asynchronous boundary elsewhere."
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
                if (async && !facts.hasSupportedAsyncReturnType(method)) {
                    issues.add(issue(
                            sourceUnit,
                            JavaSourceInspector.lineOf(method),
                            "@Async method '" + method.getNameAsString() + "' returns '" + method.getType() + "'; use void or a Future-compatible type."
                    ));
                }
            }
        }
        return issues;
    }
}
