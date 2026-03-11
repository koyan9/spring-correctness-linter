/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;

import java.util.ArrayList;
import java.util.List;

public final class TransactionalEventListenerRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_TRANSACTIONAL_EVENT_LISTENER";
    }

    @Override
    public String title() {
        return "Review @TransactionalEventListener boundaries";
    }

    @Override
    public String description() {
        return "Transactional event listeners should have explicit phase intent; combining them with @Transactional may hide event timing assumptions.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.EVENTS;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method combines `@TransactionalEventListener` with `@Transactional`.",
                "Both listener phase behavior and transaction boundary semantics need to be understood together."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Some listeners intentionally open or join a transaction after a specific event phase, especially for follow-up persistence work.",
                "The rule flags ambiguous intent rather than proving the combination is always incorrect."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Split listener receipt from transactional work when phase handling and transaction handling should be reasoned about separately.",
                "If both annotations are required, document the phase expectation explicitly and cover it with integration tests."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (facts.methodFacts(null, method).hasTransactionalEventBoundaryConflict()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Method '" + method.getNameAsString() + "' combines @TransactionalEventListener with @Transactional; review whether listener phase and transaction boundary are both intentional."));
            }
        }
        return issues;
    }
}
