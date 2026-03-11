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

public final class LifecycleTransactionalBoundaryRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_LIFECYCLE_TRANSACTIONAL_BOUNDARY";
    }

    @Override
    public String title() {
        return "Review @Transactional on initialization callbacks";
    }

    @Override
    public String description() {
        return "Initialization callbacks run during bean setup, so @Transactional on @PostConstruct or afterPropertiesSet may not create the proxy-based transaction boundary maintainers expect.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.LIFECYCLE;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A bean initialization callback such as `@PostConstruct` or `InitializingBean.afterPropertiesSet()` is transactional directly or through class-level metadata.",
                "The callback runs during bean initialization, where proxy-based transaction expectations are easy to misread."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Some projects intentionally keep initialization work inside a manually managed transaction boundary, but that should be explicit.",
                "The rule is advisory and focuses on likely proxy-boundary surprises during bean startup."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Move transactional work out of initialization callbacks and into regular service methods invoked after startup.",
                "If startup work truly needs a transaction, use a clearer bootstrap phase and verify transaction behavior with integration tests."
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
                        && (typeFacts.hasTransactionalBoundary() || methodFacts.hasTransactionalBoundary())) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Initialization callback '" + method.getNameAsString() + "' uses transactional semantics; prefer running transactional work after bean initialization completes."));
                }
            }
        }
        return issues;
    }
}
