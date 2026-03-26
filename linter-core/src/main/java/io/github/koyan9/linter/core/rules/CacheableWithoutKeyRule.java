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

import java.util.ArrayList;
import java.util.List;

public final class CacheableWithoutKeyRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_CACHEABLE_KEY";
    }

    @Override
    public String title() {
        return "Declare cache keys explicitly";
    }

    @Override
    public String description() {
        return "Explicit keys reduce accidental cache collisions when method signatures evolve.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.CACHE;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method uses `@Cacheable`, has one or more parameters, and does not declare an explicit `key` or `keyGenerator`.",
                "The cache entry should remain stable even if parameters or method signatures evolve over time."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Zero-argument methods are excluded because Spring's default cache key is already stable there.",
                "Some teams intentionally rely on Spring's default key generation for simple parameterized signatures; the rule still flags those for explicit review.",
                "Projects that standardize on a custom `keyGenerator` may intentionally avoid per-method `key` declarations."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Declare an explicit SpEL `key` when cache identity should be obvious at the method declaration site.",
                "Use a shared `keyGenerator` when the project already has a stable cache-key convention.",
                "If relying on Spring's default key is intentional, suppress the finding with a reason that documents that convention.",
                "To allow specific caches to keep the default key, use `spring.correctness.linter.cacheDefaultKeyCacheNames`."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        if (context.options().autoDetectProjectWideKeyGenerator() && context.hasProjectWideKeyGeneratorBean()) {
            return List.of();
        }
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                List<SpringSemanticFacts.CacheableOperation> cacheableOperations = facts.cacheableOperations(typeDeclaration, method);
                if (cacheableOperations.isEmpty() || method.getParameters().isEmpty()) {
                    continue;
                }
                if (cacheableOperations.stream().anyMatch(operation -> !operation.explicitKeyStrategy())) {
                    if (!isDefaultCacheKeyAllowed(typeDeclaration, cacheableOperations, facts, context.options().cacheDefaultKeyCacheNames())) {
                        issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Cacheable method '" + method.getNameAsString() + "' does not declare an explicit cache key strategy."));
                    }
                }
            }
        }
        return issues;
    }

    private boolean isDefaultCacheKeyAllowed(
            TypeDeclaration<?> typeDeclaration,
            List<SpringSemanticFacts.CacheableOperation> cacheableOperations,
            SpringSemanticFacts facts,
            java.util.Set<String> cacheNames
    ) {
        if (cacheNames.isEmpty()) {
            return false;
        }
        if (cacheNames.contains("*")) {
            return true;
        }
        java.util.Set<String> typeCacheNames = facts.typeCacheNames(typeDeclaration);
        for (SpringSemanticFacts.CacheableOperation operation : cacheableOperations) {
            if (operation.explicitKeyStrategy()) {
                continue;
            }
            java.util.Set<String> effectiveCacheNames = operation.cacheNames().isEmpty() ? typeCacheNames : operation.cacheNames();
            if (effectiveCacheNames.isEmpty()) {
                return false;
            }
            boolean allowed = effectiveCacheNames.stream()
                    .filter(cacheName -> !cacheName.isBlank())
                    .anyMatch(cacheNames::contains);
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
