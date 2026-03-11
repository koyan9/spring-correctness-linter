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

public final class ScheduledMethodArgumentsRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_SCHEDULED_METHOD_PARAMETERS";
    }

    @Override
    public String title() {
        return "Avoid parameters on @Scheduled methods";
    }

    @Override
    public String description() {
        return "Scheduled methods are invoked by the scheduler and must not declare method parameters.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.SCHEDULED;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method is scheduled with `@Scheduled` or a composed scheduling annotation.",
                "The scheduled method declares one or more parameters."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "No common false-positive pattern is currently expected because scheduled methods are invoked without caller-supplied arguments.",
                "If a framework-specific extension intentionally injects parameters, document that contract and suppress locally."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Move any required input lookup inside the scheduled method body or into collaborator beans.",
                "Keep the scheduled method signature argument-free and delegate to parameterized internal methods when needed."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (facts.methodFacts(null, method).isScheduledMethodWithParameters()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Scheduled method '" + method.getNameAsString() + "' declares parameters; scheduled methods should be argument-free."));
            }
        }
        return issues;
    }
}
