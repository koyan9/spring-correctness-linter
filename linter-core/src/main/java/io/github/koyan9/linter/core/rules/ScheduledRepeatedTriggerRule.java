/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;

import java.util.ArrayList;
import java.util.List;

public final class ScheduledRepeatedTriggerRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_SCHEDULED_REPEATED_TRIGGER";
    }

    @Override
    public String title() {
        return "Review repeated @Scheduled declarations";
    }

    @Override
    public String description() {
        return "Multiple scheduled declarations on the same method create multiple trigger registrations and can lead to overlapping executions.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.SCHEDULED;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method has more than one direct or composed scheduling annotation.",
                "Spring will register multiple schedules for the same method body."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Repeatable scheduling can be intentional for carefully controlled multi-trigger jobs.",
                "The rule is advisory and focuses on making concurrent trigger behavior explicit."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Split distinct schedules into separate methods when overlap and execution semantics should be isolated.",
                "If repeated scheduling is intentional, document overlap expectations and verify them with integration tests."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            long scheduledCount = facts.annotationMatchCount(method, "Scheduled");
            if (scheduledCount > 1) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Method '" + method.getNameAsString() + "' has multiple scheduling declarations; review overlap and duplicate trigger semantics."));
            }
        }
        return issues;
    }
}
