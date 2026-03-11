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
    public RuleDomain domain() {
        return RuleDomain.TRANSACTION;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method is both `final` and annotated with `@Transactional`.",
                "The application may use class-based proxying where final methods cannot be intercepted."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Interface-based proxying or AspectJ weaving may reduce or eliminate the practical impact for some projects.",
                "The rule intentionally warns early because proxy mode can change across environments or future refactors."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Remove the `final` modifier from the transactional method when proxy-based interception is expected.",
                "Move the transaction boundary to a separate, proxied service method if the current method must stay final."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (facts.methodFacts(null, method).isTransactionalFinalMethod()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Transactional method '" + method.getNameAsString() + "' is final; class-based proxies cannot advise final methods."));
            }
        }
        return issues;
    }
}
