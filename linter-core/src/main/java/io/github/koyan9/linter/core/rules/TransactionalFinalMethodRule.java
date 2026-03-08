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

public final class TransactionalFinalMethodRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_TX_FINAL_METHOD";
    }

    @Override
    public String title() {
        return "Avoid final @Transactional methods";
    }

    @Override
    public String description() {
        return "Final @Transactional methods cannot be advised when class-based proxies are used.";
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (JavaSourceInspector.hasAnnotation(method, "Transactional") && method.isFinal()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Transactional method '" + method.getNameAsString() + "' is final; class-based proxies cannot advise final methods."));
            }
        }
        return issues;
    }
}
