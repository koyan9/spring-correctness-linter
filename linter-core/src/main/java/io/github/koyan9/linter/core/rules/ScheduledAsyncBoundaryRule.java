/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.MethodSemanticFacts;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;

import java.util.ArrayList;
import java.util.List;

public final class ScheduledAsyncBoundaryRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_SCHEDULED_ASYNC_BOUNDARY";
    }

    @Override
    public String title() {
        return "Review @Scheduled and @Async combinations";
    }

    @Override
    public String description() {
        return "Combining @Scheduled with @Async changes scheduling, overlap, and failure-observation semantics; keep the boundary explicit.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.SCHEDULED;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method is both scheduled and asynchronous, either directly or through composed annotations.",
                "The scheduler trigger and the async handoff together affect overlap and error visibility."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "This combination can be intentional for short trigger methods that only enqueue work onto another executor.",
                "The rule is advisory and does not assume the combination is always wrong."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Keep scheduling and asynchronous handoff in separate methods or beans when the boundary should be obvious to maintainers.",
                "If the combination is intentional, document overlap and failure-handling expectations and cover them with integration tests."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            MethodSemanticFacts methodFacts = facts.methodFacts(null, method);
            if (methodFacts.isScheduledAsyncBoundary()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Method '" + method.getNameAsString() + "' combines @Scheduled with @Async; review overlap, executor handoff, and failure-observation semantics."));
            }
        }
        return issues;
    }
}
