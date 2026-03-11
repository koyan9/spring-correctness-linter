/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.MethodSemanticFacts;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;
import io.github.koyan9.linter.core.TypeSemanticFacts;

import java.util.ArrayList;
import java.util.List;

public final class LifecycleAsyncBoundaryRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_LIFECYCLE_ASYNC_BOUNDARY";
    }

    @Override
    public String title() {
        return "Review @Async on initialization callbacks";
    }

    @Override
    public String description() {
        return "Initialization callbacks run during bean setup, so @Async on @PostConstruct or afterPropertiesSet is easy to misread and may not provide the intended proxy-based boundary.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.LIFECYCLE;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A bean initialization callback such as `@PostConstruct` or `InitializingBean.afterPropertiesSet()` is also asynchronous.",
                "The bean has not completed normal proxy-oriented lifecycle setup when the callback runs."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Some projects intentionally use initialization callbacks only to enqueue work elsewhere, but the async boundary should still be explicit.",
                "The rule is advisory and focuses on bean-lifecycle clarity rather than proving a runtime failure in every environment."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Keep initialization callbacks synchronous and delegate follow-up asynchronous work to a regular bean method invoked after startup.",
                "Prefer an application event, runner, or explicit bootstrap coordinator when async startup work must happen after bean initialization."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            TypeSemanticFacts typeFacts = facts.typeFacts(typeDeclaration);
            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                MethodSemanticFacts methodFacts = facts.methodFacts(typeDeclaration, method);
                if (methodFacts.initializationCallback()
                        && (typeFacts.hasAsyncBoundary() || methodFacts.hasAsyncBoundary())) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Initialization callback '" + method.getNameAsString() + "' uses @Async semantics; prefer triggering async work after bean initialization completes."));
                }
            }
        }
        return issues;
    }
}
