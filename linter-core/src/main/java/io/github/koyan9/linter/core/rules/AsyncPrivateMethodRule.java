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

public final class AsyncPrivateMethodRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_ASYNC_PRIVATE_METHOD";
    }

    @Override
    public String title() {
        return "Avoid @Async on private methods";
    }

    @Override
    public String description() {
        return "Private @Async methods are not proxied by Spring AOP and asynchronous execution will not be applied.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.ASYNC;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method annotated with `@Async` is declared `private` inside a Spring-managed type.",
                "The project relies on proxy-based Spring AOP for asynchronous method interception."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Projects using AspectJ weaving instead of proxy-based interception may intentionally accept patterns that this rule flags.",
                "Methods in non-managed classes are still suspicious, but the runtime symptom may differ from a proxied Spring bean."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Make the async entrypoint non-private and invoke it through a Spring bean proxy.",
                "Move the async boundary into a separate bean if the current method must remain private."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (facts.methodFacts(null, method).isAsyncPrivateMethod()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Async method '" + method.getNameAsString() + "' is private and will not be advised by Spring proxies."));
            }
        }
        return issues;
    }
}
