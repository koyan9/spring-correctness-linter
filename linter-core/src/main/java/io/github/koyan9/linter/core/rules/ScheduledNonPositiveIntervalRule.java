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
import java.util.OptionalLong;

public final class ScheduledNonPositiveIntervalRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_SCHEDULED_NON_POSITIVE_INTERVAL";
    }

    @Override
    public String title() {
        return "Review non-positive @Scheduled intervals";
    }

    @Override
    public String description() {
        return "Non-positive fixed-rate, fixed-delay, or initial-delay values are easy to misconfigure and can produce invalid or pathological schedules.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.SCHEDULED;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A scheduled method sets a numeric or numeric-string interval to `0` or a negative value.",
                "The resulting schedule could be invalid, disabled by mistake, or effectively unbounded."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Dynamic placeholders or non-numeric expressions are intentionally skipped unless they resolve to obvious numeric literals in source.",
                "The rule only flags clearly non-positive literal intervals, not values computed at runtime."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Use a strictly positive interval for `fixedRate`, `fixedDelay`, and `initialDelay` members.",
                "Prefer explicit, readable delay values that make scheduler cadence obvious to maintainers."
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
            checkInterval(sourceUnit, facts, method, issues, "fixedDelay");
            checkInterval(sourceUnit, facts, method, issues, "fixedDelayString");
            checkInterval(sourceUnit, facts, method, issues, "fixedRate");
            checkInterval(sourceUnit, facts, method, issues, "fixedRateString");
            checkInterval(sourceUnit, facts, method, issues, "initialDelay");
            checkInterval(sourceUnit, facts, method, issues, "initialDelayString");
        }
        return issues;
    }

    private void checkInterval(SourceUnit sourceUnit, SpringSemanticFacts facts, MethodDeclaration method, List<LintIssue> issues, String memberName) {
        OptionalLong parsedValue = parseNumericMember(method, facts, memberName);
        if (parsedValue.isPresent() && parsedValue.getAsLong() <= 0) {
            issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Scheduled method '" + method.getNameAsString() + "' uses non-positive " + memberName + "=" + parsedValue.getAsLong() + "; use a strictly positive interval."));
        }
    }

    private OptionalLong parseNumericMember(MethodDeclaration method, SpringSemanticFacts facts, String memberName) {
        return facts.annotationMemberValue(method, "Scheduled", memberName)
                .map(this::parseLongLiteral)
                .orElse(OptionalLong.empty());
    }

    private OptionalLong parseLongLiteral(String rawValue) {
        String value = rawValue.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.startsWith("${") || value.startsWith("#{") || value.isBlank()) {
            return OptionalLong.empty();
        }
        value = value.replace("_", "");
        if (value.endsWith("L") || value.endsWith("l")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            return OptionalLong.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }
}
