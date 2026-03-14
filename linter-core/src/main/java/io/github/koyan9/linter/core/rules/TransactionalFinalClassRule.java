/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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

public final class TransactionalFinalClassRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_TX_FINAL_CLASS";
    }

    @Override
    public String title() {
        return "Avoid final @Transactional classes";
    }

    @Override
    public String description() {
        return "Final classes cannot be proxied by class-based proxies; without interfaces, @Transactional advice will not apply.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.TRANSACTION;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A final class declares @Transactional boundaries and does not implement interfaces.",
                "The project relies on proxy-based transaction interception rather than AspectJ weaving."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "AspectJ weaving or explicit proxy factories can still advise final classes in some projects.",
                "Classes that implement interfaces may still be proxied through JDK proxies, so this rule skips those to reduce noise."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Remove the `final` modifier from transactional classes when class-based proxies are expected.",
                "Introduce an interface and ensure transaction boundaries are declared on the interface methods.",
                "Move transactional work into a separate, proxied bean if the class must remain final."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            if (!(typeDeclaration instanceof ClassOrInterfaceDeclaration classDeclaration)) {
                continue;
            }
            if (classDeclaration.isInterface() || !classDeclaration.isFinal()) {
                continue;
            }
            if (!classDeclaration.getImplementedTypes().isEmpty()) {
                continue;
            }

            TypeSemanticFacts typeFacts = facts.typeFacts(typeDeclaration);
            boolean classTransactional = typeFacts.hasTransactionalBoundary();
            boolean methodTransactional = false;
            if (!classTransactional) {
                for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                    MethodSemanticFacts methodFacts = facts.methodFacts(typeDeclaration, method);
                    if (methodFacts.hasTransactionalBoundary()) {
                        methodTransactional = true;
                        break;
                    }
                }
            }

            if (classTransactional || methodTransactional) {
                issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(typeDeclaration), "Final class '" + classDeclaration.getNameAsString() + "' declares @Transactional boundaries but implements no interfaces; class-based proxies cannot intercept it."));
            }
        }
        return issues;
    }
}
