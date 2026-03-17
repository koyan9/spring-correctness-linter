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

public final class ScheduledTriggerConfigurationRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_SCHEDULED_TRIGGER_CONFIGURATION";
    }

    @Override
    public String title() {
        return "Review @Scheduled trigger configuration";
    }

    @Override
    public String description() {
        return "Scheduled methods should define one clear trigger mode, and composed scheduling annotations should not hide conflicting timing configuration.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.SCHEDULED;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A scheduled method has no effective trigger configuration.",
                "A scheduled method mixes more than one periodic trigger mode such as cron, fixed delay, or fixed rate."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "One-time scheduling with only `initialDelay` is treated as valid and is not flagged.",
                "The rule focuses on a single effective scheduling configuration and does not model repeatable `@Scheduled` declarations in depth."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Keep one periodic trigger mode per scheduled method: cron, fixed delay, or fixed rate.",
                "If scheduling metadata is composed through a custom annotation, keep the effective trigger values explicit and testable."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (!facts.hasAnnotation(method, "Scheduled")) {
                continue;
            }

            SpringSemanticFacts.ScheduledTriggerSummary summary = facts.scheduledTriggerSummary(method);

            if (summary.periodicConfiguredCount() == 0 && !summary.initialDelayConfigured()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Scheduled method '" + method.getNameAsString() + "' does not declare an effective trigger configuration."));
                continue;
            }
            if (summary.periodicLiteralCount() > 1 && !summary.periodicHasPlaceholder()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Scheduled method '" + method.getNameAsString() + "' combines multiple trigger modes; keep a single periodic schedule configuration."));
            }
        }
        return issues;
    }
}
