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
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;
import io.github.koyan9.linter.core.TypeSemanticFacts;

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
    public RuleDomain domain() {
        return RuleDomain.EVENTS;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "An `@EventListener` method is also transactional, either directly or via a class-level `@Transactional` annotation.",
                "Listener timing relative to transaction boundaries could affect correctness or observability."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Some listeners intentionally run inside an ambient transaction and do not care about explicit transaction phases.",
                "The rule does not decide whether the current boundary is wrong; it flags places where the intent should be explicit."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Use `@TransactionalEventListener` when a specific transaction phase matters to the listener behavior.",
                "If the listener should run transactionally as written, document that choice and suppress locally with a reason."
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
                if (methodFacts.isEventListenerTransactionalBoundary(typeFacts.hasTransactionalBoundary())) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Event listener method '" + method.getNameAsString() + "' runs with @Transactional semantics; if transaction phase matters, prefer @TransactionalEventListener or document the boundary explicitly."));
                }
            }
        }
        return issues;
    }
}
