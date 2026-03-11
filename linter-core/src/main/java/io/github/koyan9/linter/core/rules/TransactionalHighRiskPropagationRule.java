/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.MethodSemanticFacts;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;

import java.util.ArrayList;
import java.util.List;

public final class TransactionalHighRiskPropagationRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_TX_HIGH_RISK_PROPAGATION";
    }

    @Override
    public String title() {
        return "Review high-risk transaction propagation";
    }

    @Override
    public String description() {
        return "Propagation modes like REQUIRES_NEW and NESTED can change transaction boundaries, connection usage, and failure semantics.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.TRANSACTION;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A `@Transactional` method explicitly uses `REQUIRES_NEW` or `NESTED` propagation.",
                "The method may change rollback, connection, or caller-expectation semantics compared with default propagation."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "These propagation modes can be correct for audit logging, outbox writes, compensation, or other isolated transaction flows.",
                "The rule does not inspect surrounding architectural guarantees, retries, or resource-pool sizing."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Keep the high-risk propagation scope as small as possible and document why the non-default boundary is required.",
                "Verify rollback behavior, connection usage, and caller expectations with focused integration tests."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            MethodSemanticFacts methodFacts = facts.methodFacts(null, method);
            if (methodFacts.hasHighRiskTransactionPropagation()) {
                String propagation = methodFacts.highRiskTransactionPropagationName();
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Transactional method '" + method.getNameAsString() + "' uses propagation " + propagation + "; review connection usage, rollback semantics, and calling expectations."));
            }
        }
        return issues;
    }
}
