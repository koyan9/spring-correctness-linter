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

public final class TransactionalPrivateMethodRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_TX_PRIVATE_METHOD";
    }

    @Override
    public String title() {
        return "Avoid @Transactional on private methods";
    }

    @Override
    public String description() {
        return "Private @Transactional methods are not proxied by Spring AOP and transaction advice will not run.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.TRANSACTION;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method annotated with `@Transactional` is declared `private`.",
                "The transaction boundary is expected to be created by Spring proxy advice."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Projects using AspectJ weaving instead of proxy-based advice may intentionally tolerate private transactional methods.",
                "The rule does not infer whether the method is effectively unreachable from any Spring-managed call path."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Move the transaction boundary to a non-private method that can be advised by Spring.",
                "Extract the transactional work into another bean if the implementation detail must remain private."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (facts.methodFacts(null, method).isTransactionalPrivateMethod()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Transactional method '" + method.getNameAsString() + "' is private and will not be advised by Spring proxies."));
            }
        }
        return issues;
    }
}
