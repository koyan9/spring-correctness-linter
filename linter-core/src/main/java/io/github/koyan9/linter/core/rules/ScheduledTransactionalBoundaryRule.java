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

public final class ScheduledTransactionalBoundaryRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_SCHEDULED_TRANSACTIONAL_BOUNDARY";
    }

    @Override
    public String title() {
        return "Review @Scheduled transactional boundaries";
    }

    @Override
    public String description() {
        return "Scheduled methods that run transactionally can hide long-running transactions, retry semantics, and scheduler-thread behavior.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.SCHEDULED;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A scheduled method is transactional directly or inherits a class-level transactional boundary.",
                "The background execution context could hold transactions open longer than intended."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Some batch or cleanup jobs intentionally run in a transaction and are correct when carefully bounded.",
                "The rule is advisory and focuses on making scheduler-thread transaction scope explicit."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Keep the scheduled entrypoint thin and delegate transactional work to a clearly named service method when possible.",
                "If the scheduled method itself must be transactional, verify transaction length, retry behavior, and scheduler interaction with focused tests."
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
                if (methodFacts.isScheduledTransactionalBoundary(typeFacts.hasTransactionalBoundary())) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Method '" + method.getNameAsString() + "' combines @Scheduled with transactional semantics; review scheduler-thread transaction scope and failure handling."));
                }
            }
        }
        return issues;
    }
}
