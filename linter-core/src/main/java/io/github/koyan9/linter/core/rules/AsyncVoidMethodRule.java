/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;

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
    public RuleDomain domain() {
        return RuleDomain.ASYNC;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A Spring-managed bean method is annotated with `@Async` and returns `void`.",
                "Callers may need a completion handle to observe failures, retries, or downstream chaining."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Fire-and-forget background work can be intentional when exceptions are handled centrally, for example via `AsyncUncaughtExceptionHandler`.",
                "Framework callback methods may constrain the return type even when asynchronous execution is still desired."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Return `CompletableFuture`, `CompletionStage`, or another observable async type instead of `void`.",
                "If fire-and-forget behavior is intentional, document that choice and suppress the finding locally."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (facts.methodFacts(null, method).isAsyncVoidMethod()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Async method '" + method.getNameAsString() + "' returns void; prefer CompletableFuture or another observable type."));
            }
        }
        return issues;
    }
}
