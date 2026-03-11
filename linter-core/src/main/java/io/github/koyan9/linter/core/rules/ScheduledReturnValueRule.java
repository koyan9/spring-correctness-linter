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
import java.util.Set;

public final class ScheduledReturnValueRule extends AbstractSpringRule {

    private static final Set<String> REACTIVE_RETURN_TYPES = Set.of(
            "Publisher",
            "Mono",
            "Flux",
            "CompletionStage",
            "CompletableFuture"
    );

    @Override
    public String id() {
        return "SPRING_SCHEDULED_RETURN_VALUE";
    }

    @Override
    public String title() {
        return "Review non-void @Scheduled return types";
    }

    @Override
    public String description() {
        return "Scheduled return values are easy to misread as part of the contract; prefer void unless reactive scheduling semantics are intentional.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.SCHEDULED;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A scheduled method returns a regular non-void type.",
                "Callers might incorrectly assume the return value participates in the scheduling contract."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Reactive or publisher-style return types can be intentional in newer Spring scheduling scenarios.",
                "This rule intentionally skips common reactive return types to reduce false positives."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Use `void` when the scheduled work is fully side-effect driven.",
                "If reactive scheduling is intentional, use a clearly supported reactive return type and document the design choice."
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
            if (method.getType().isVoidType()) {
                continue;
            }
            String simpleTypeName = method.getType().asString();
            int genericIndex = simpleTypeName.indexOf('<');
            if (genericIndex >= 0) {
                simpleTypeName = simpleTypeName.substring(0, genericIndex);
            }
            int packageIndex = simpleTypeName.lastIndexOf('.');
            if (packageIndex >= 0) {
                simpleTypeName = simpleTypeName.substring(packageIndex + 1);
            }
            if (REACTIVE_RETURN_TYPES.contains(simpleTypeName)) {
                continue;
            }
            issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Scheduled method '" + method.getNameAsString() + "' returns '" + method.getType() + "'; prefer void unless reactive scheduling is explicitly intended."));
        }
        return issues;
    }
}
