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

public final class StartupTransactionalBoundaryRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_STARTUP_TRANSACTIONAL_BOUNDARY";
    }

    @Override
    public String title() {
        return "Review transactional startup lifecycle callbacks";
    }

    @Override
    public String description() {
        return "Startup lifecycle callbacks combined with @Transactional can hide bootstrap transaction scope and readiness semantics.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.LIFECYCLE;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A startup lifecycle callback such as `ApplicationRunner`, `CommandLineRunner`, or `SmartInitializingSingleton` uses transactional semantics.",
                "Bootstrap work may depend on proxy-based transaction behavior during application startup."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Some startup data initialization flows intentionally run in a transaction, but the readiness and rollback expectations should be explicit.",
                "The rule is advisory and focuses on startup transaction clarity rather than rejecting every transactional bootstrap phase."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Keep bootstrap callbacks thin and delegate transactional work to clearly named services when possible.",
                "If startup must be transactional, verify transaction boundaries and readiness behavior with integration tests."
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
                        && (typeFacts.hasTransactionalBoundary() || methodFacts.hasTransactionalBoundary())) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Startup lifecycle callback '" + method.getNameAsString() + "' uses transactional semantics; review startup transaction scope and readiness behavior."));
                }
            }
        }
        return issues;
    }
}
