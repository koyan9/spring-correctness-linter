/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.MethodSemanticFacts;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CacheAnnotationCombinationRiskRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_CACHE_COMBINATION_RISK";
    }

    @Override
    public String title() {
        return "Review risky cache annotation combinations";
    }

    @Override
    public String description() {
        return "Combining multiple cache annotations on the same method can make cache semantics hard to reason about and may hide stale-data bugs.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.CACHE;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "The same method combines two or more of `@Cacheable`, `@CachePut`, or `@CacheEvict`.",
                "Cache population, refresh, and eviction behavior are coupled into one execution path."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Some carefully designed cache workflows intentionally combine annotations and remain correct when thoroughly tested.",
                "The rule does not inspect `condition`, `unless`, or custom cache manager behavior in depth."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Split cache population and eviction responsibilities into separate methods when possible.",
                "If the combination is intentional, document the order and semantics with tests and a local suppression reason."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            MethodSemanticFacts methodFacts = facts.methodFacts(null, method);
            if (methodFacts.hasConflictingCacheAnnotations()) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Method '" + method.getNameAsString() + "' combines multiple cache annotations; review whether cache population and eviction semantics are explicit and safe."));
            }
        }
        return issues;
    }
}
