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
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;
import io.github.koyan9.linter.core.TypeSemanticFacts;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ConditionalOnBeanConflictRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_CONDITIONAL_BEAN_CONFLICT";
    }

    @Override
    public String title() {
        return "Detect conflicting conditional bean annotations";
    }

    @Override
    public String description() {
        return "Using @ConditionalOnBean and @ConditionalOnMissingBean together on the same type or bean method is usually contradictory.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.CONFIGURATION;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "The same bean method or type declares both `@ConditionalOnBean` and `@ConditionalOnMissingBean`.",
                "Bean registration logic becomes difficult to reason about from the annotation combination alone."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "The rule does not compare detailed attribute combinations, so rare intentional patterns may still be flagged.",
                "Projects with additional custom conditions may rely on semantics that are not visible to this check."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Split contradictory conditions into separate bean declarations when each branch has a distinct purpose.",
                "Prefer one clear condition per bean definition and move more complex logic into dedicated configuration code."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            TypeSemanticFacts typeFacts = facts.typeFacts(typeDeclaration);
            if (typeFacts.hasConflictingConditionalBeanAnnotations()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(typeDeclaration), "Element '" + typeDeclaration.getNameAsString() + "' declares both @ConditionalOnBean and @ConditionalOnMissingBean; review the condition semantics."));
            }
            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                MethodSemanticFacts methodFacts = facts.methodFacts(typeDeclaration, method);
                if (methodFacts.hasConflictingConditionalBeanAnnotations()) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Element '" + method.getNameAsString() + "' declares both @ConditionalOnBean and @ConditionalOnMissingBean; review the condition semantics."));
                }
            }
        }
        return issues;
    }
}
