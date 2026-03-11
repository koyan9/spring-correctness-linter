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

public final class StartupAsyncBoundaryRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_STARTUP_ASYNC_BOUNDARY";
    }

    @Override
    public String title() {
        return "Review async startup lifecycle callbacks";
    }

    @Override
    public String description() {
        return "Startup lifecycle callbacks combined with @Async can hide application readiness and failure-observation semantics during bootstrap.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.LIFECYCLE;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A startup lifecycle callback such as `ApplicationRunner`, `CommandLineRunner`, or `SmartInitializingSingleton` uses async semantics.",
                "Bootstrap readiness and startup ordering depend on work that may continue after the callback returns."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Some applications intentionally offload slow startup work asynchronously, but the readiness contract should remain explicit.",
                "The rule is advisory and focuses on startup sequencing clarity rather than assuming every async startup hook is incorrect."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Keep startup lifecycle callbacks synchronous when application readiness depends on their work completing.",
                "If startup work must continue asynchronously, document readiness expectations and separate the async boundary from the bootstrap callback."
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
                if (methodFacts.startupLifecycleMethod()
                        && (typeFacts.hasAsyncBoundary() || methodFacts.hasAsyncBoundary())) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Startup lifecycle callback '" + method.getNameAsString() + "' uses @Async semantics; review application readiness and startup failure visibility."));
                }
            }
        }
        return issues;
    }
}
