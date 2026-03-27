/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;

import java.util.ArrayList;
import java.util.List;

public final class CacheablePrivateMethodRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_CACHEABLE_PRIVATE_METHOD";
    }

    @Override
    public String title() {
        return "Avoid @Cacheable on private methods";
    }

    @Override
    public String description() {
        return "Private @Cacheable methods are not advised by Spring cache proxies and cache interception will not run.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.CACHE;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method uses `@Cacheable` semantics and is declared `private`.",
                "The cache boundary is expected to be created by Spring proxy interception."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Projects using AspectJ weaving or direct cache APIs instead of proxy-based interception may intentionally tolerate this pattern.",
                "The rule focuses on annotation-driven cache interception and does not infer whether the private method is currently reachable from a Spring-managed call path."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Move the cache boundary to a non-private method that can be intercepted by Spring.",
                "Extract the cached work into another bean if the implementation detail must remain private.",
                "Use direct cache APIs when the intent is explicit in-method cache coordination rather than annotation-driven interception."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                if (method.isPrivate() && facts.methodFacts(typeDeclaration, method).hasCacheableOperation()) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Cacheable method '" + method.getNameAsString() + "' is private and will not be intercepted by Spring cache proxies."));
                }
            }
        }
        return issues;
    }
}
