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

import java.util.ArrayList;
import java.util.List;

public final class CacheableFinalMethodRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_CACHEABLE_FINAL_METHOD";
    }

    @Override
    public String title() {
        return "Avoid final @Cacheable methods";
    }

    @Override
    public String description() {
        return "Final @Cacheable methods cannot be intercepted when class-based proxies are used.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.CACHE;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method uses `@Cacheable` semantics and is also declared `final`.",
                "The application may use class-based proxying where final methods cannot be intercepted."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Interface-based proxies or AspectJ weaving may reduce or eliminate the practical impact for some projects.",
                "The rule intentionally warns early because proxy mode can change across environments or future refactors."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Remove the `final` modifier from the cached method when proxy-based interception is expected.",
                "Move the cache boundary to a separate, proxied bean method if the current method must stay final.",
                "Prefer explicit cache APIs if the method is intentionally final and should not depend on annotation-driven interception."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                MethodSemanticFacts methodFacts = facts.methodFacts(typeDeclaration, method);
                if (methodFacts.hasCacheableOperation() && method.isFinal()) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "@Cacheable method '" + method.getNameAsString() + "' is final; class-based proxies cannot intercept final methods."));
                }
            }
        }
        return issues;
    }
}
