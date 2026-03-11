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

            TriggerValue cron = stringTriggerValue(method, facts, "Scheduled", "cron");
            TriggerValue fixedDelay = merge(
                    numericTriggerValue(method, facts, "Scheduled", "fixedDelay"),
                    stringTriggerValue(method, facts, "Scheduled", "fixedDelayString")
            );
            TriggerValue fixedRate = merge(
                    numericTriggerValue(method, facts, "Scheduled", "fixedRate"),
                    stringTriggerValue(method, facts, "Scheduled", "fixedRateString")
            );
            TriggerValue initialDelay = merge(
                    numericTriggerValue(method, facts, "Scheduled", "initialDelay"),
                    stringTriggerValue(method, facts, "Scheduled", "initialDelayString")
            );

            int periodicConfigured = (cron.configured ? 1 : 0) + (fixedDelay.configured ? 1 : 0) + (fixedRate.configured ? 1 : 0);
            int periodicLiteral = (cron.literal ? 1 : 0) + (fixedDelay.literal ? 1 : 0) + (fixedRate.literal ? 1 : 0);
            boolean periodicHasPlaceholder = cron.placeholder || fixedDelay.placeholder || fixedRate.placeholder;

            if (periodicConfigured == 0 && !initialDelay.configured) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Scheduled method '" + method.getNameAsString() + "' does not declare an effective trigger configuration."));
                continue;
            }
            if (periodicLiteral > 1 && !periodicHasPlaceholder) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Scheduled method '" + method.getNameAsString() + "' combines multiple trigger modes; keep a single periodic schedule configuration."));
            }
        }
        return issues;
    }

    private TriggerValue stringTriggerValue(MethodDeclaration method, SpringSemanticFacts facts, String annotationName, String memberName) {
        return facts.annotationMemberValue(method, annotationName, memberName)
                .map(this::parseStringTriggerValue)
                .orElse(TriggerValue.missing());
    }

    private TriggerValue numericTriggerValue(MethodDeclaration method, SpringSemanticFacts facts, String annotationName, String memberName) {
        return facts.annotationMemberValue(method, annotationName, memberName)
                .map(this::parseNumericTriggerValue)
                .orElse(TriggerValue.missing());
    }

    private TriggerValue parseStringTriggerValue(String rawValue) {
        String trimmed = rawValue.trim();
        if (trimmed.isBlank()) {
            return TriggerValue.missing();
        }
        boolean quoted = trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"");
        String normalized = quoted ? trimmed.substring(1, trimmed.length() - 1).trim() : trimmed;
        if (normalized.isBlank()) {
            return TriggerValue.missing();
        }
        if (!quoted || isPlaceholderValue(normalized)) {
            return TriggerValue.placeholder();
        }
        return TriggerValue.literal();
    }

    private TriggerValue parseNumericTriggerValue(String rawValue) {
        String normalized = normalizeNumericLiteral(rawValue);
        if (normalized.isBlank()) {
            return TriggerValue.missing();
        }
        if (isPlaceholderValue(normalized)) {
            return TriggerValue.placeholder();
        }
        try {
            long parsed = Long.parseLong(normalized);
            if (parsed == -1) {
                return TriggerValue.missing();
            }
        } catch (NumberFormatException exception) {
            return TriggerValue.placeholder();
        }
        return TriggerValue.literal();
    }

    private boolean isPlaceholderValue(String value) {
        return value.contains("${") || value.contains("#{");
    }

    private String normalizeNumericLiteral(String value) {
        String normalized = value.trim().replace("_", "");
        if (normalized.endsWith("L") || normalized.endsWith("l")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private TriggerValue merge(TriggerValue left, TriggerValue right) {
        if (left.configured || right.configured) {
            if (left.placeholder || right.placeholder) {
                return TriggerValue.placeholder();
            }
            return TriggerValue.literal();
        }
        return TriggerValue.missing();
    }

    private static final class TriggerValue {

        private final boolean configured;
        private final boolean literal;
        private final boolean placeholder;

        private TriggerValue(boolean configured, boolean literal, boolean placeholder) {
            this.configured = configured;
            this.literal = literal;
            this.placeholder = placeholder;
        }

        private static TriggerValue missing() {
            return new TriggerValue(false, false, false);
        }

        private static TriggerValue literal() {
            return new TriggerValue(true, true, false);
        }

        private static TriggerValue placeholder() {
            return new TriggerValue(true, false, true);
        }
    }
}
