/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;
import io.github.koyan9.linter.core.TypeSemanticFacts;

import java.util.ArrayList;
import java.util.List;

public final class ProfileOnControllerRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_PROFILE_CONTROLLER";
    }

    @Override
    public String title() {
        return "Avoid @Profile directly on controllers";
    }

    @Override
    public String description() {
        return "Hiding controllers by profile can create environment-specific API drift and unclear contract changes.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.WEB;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A controller type is annotated directly with `@Profile`.",
                "Different runtime environments may expose different endpoint sets from the same codebase."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Internal-only or temporary operational endpoints may intentionally be profile-gated.",
                "The rule does not know whether the API difference is already documented and contract-tested elsewhere."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Move environment switching into service wiring or delegation instead of hiding the controller itself.",
                "Use feature flags or explicit routing choices when endpoint visibility is intentionally environment-specific."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        collectIssues(sourceUnit, context, issues);
        return issues;
    }

    private void collectIssues(SourceUnit sourceUnit, ProjectContext context, List<LintIssue> issues) {
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            TypeSemanticFacts typeFacts = facts.typeFacts(typeDeclaration);
            if (typeFacts.isProfileScopedController()) {
                issues.add(issue(
                        sourceUnit,
                        JavaSourceInspector.lineOf(typeDeclaration),
                        "Controller '" + typeDeclaration.getNameAsString() + "' is gated by @Profile; prefer routing, feature flags, or conditional delegation."
                ));
            }
        }
    }
}
