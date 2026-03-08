/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;

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
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            boolean requiresNew = JavaSourceInspector.annotationMemberContains(method, "Transactional", "propagation", "REQUIRES_NEW");
            boolean nested = JavaSourceInspector.annotationMemberContains(method, "Transactional", "propagation", "NESTED");
            if (requiresNew || nested) {
                String propagation = requiresNew ? "REQUIRES_NEW" : "NESTED";
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Transactional method '" + method.getNameAsString() + "' uses propagation " + propagation + "; review connection usage, rollback semantics, and calling expectations."));
            }
        }
        return issues;
    }
}
