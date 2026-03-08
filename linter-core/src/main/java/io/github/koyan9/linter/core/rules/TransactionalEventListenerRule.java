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
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (JavaSourceInspector.hasAnnotation(method, "TransactionalEventListener")
                    && JavaSourceInspector.hasAnnotation(method, "Transactional")) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Method '" + method.getNameAsString() + "' combines @TransactionalEventListener with @Transactional; review whether listener phase and transaction boundary are both intentional."));
            }
        }
        return issues;
    }
}
