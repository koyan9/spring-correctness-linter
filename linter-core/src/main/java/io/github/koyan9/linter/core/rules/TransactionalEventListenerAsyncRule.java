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

public final class TransactionalEventListenerAsyncRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_TRANSACTIONAL_EVENT_LISTENER_ASYNC";
    }

    @Override
    public String title() {
        return "Review @TransactionalEventListener async boundaries";
    }

    @Override
    public String description() {
        return "Combining @TransactionalEventListener with @Async makes listener phase timing, executor handoff, and failure observation harder to reason about.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.EVENTS;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method uses `@TransactionalEventListener` and is asynchronous directly or through a class-level `@Async` boundary.",
                "Listener phase behavior and cross-thread execution semantics both matter to correctness and observability."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Some listeners intentionally switch threads after a transaction phase and remain correct when executor behavior and retries are well understood.",
                "The rule is advisory and flags places where phase timing, delivery guarantees, and error handling should be made explicit."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Keep the transactional event listener thin and delegate asynchronous follow-up work to a clearly named service when possible.",
                "If asynchronous listener delivery is intentional, document the expected transaction phase, executor choice, and failure-handling behavior with integration tests."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            TypeSemanticFacts typeFacts = facts.typeFacts(typeDeclaration);
            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                MethodSemanticFacts methodFacts = facts.methodFacts(typeDeclaration, method);
                boolean async = methodFacts.hasAsyncBoundary() || (typeFacts.hasAsyncBoundary() && method.isPublic());
                if (methodFacts.hasTransactionalEventListenerBoundary() && async) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Method '" + method.getNameAsString() + "' combines @TransactionalEventListener with @Async; review transaction phase timing, executor handoff, and failure observation."));
                }
            }
        }
        return issues;
    }
}
