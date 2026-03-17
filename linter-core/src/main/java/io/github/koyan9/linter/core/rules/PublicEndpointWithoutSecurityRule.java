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
import io.github.koyan9.linter.core.TypeResolutionIndex;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PublicEndpointWithoutSecurityRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_ENDPOINT_SECURITY";
    }

    @Override
    public String title() {
        return "Flag public endpoints without security annotations";
    }

    @Override
    public String description() {
        return "Public controller methods should have explicit security intent at class or method level.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.WEB;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A public request-mapped controller method has no recognized security annotation at method or class level.",
                "The project expects endpoint access policy to be visible near the controller entrypoint."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Security may be enforced centrally in `SecurityFilterChain`, an API gateway, or another infrastructure layer.",
                "Custom security annotations that are not meta-annotated with Spring Security annotations will not be recognized automatically.",
                "The rule currently recognizes common annotation-based intent but does not fully model external security configuration."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Add explicit method-level or class-level security annotations when that is the project convention.",
                "If security is intentionally centralized elsewhere, suppress the finding with a reason that points to that policy location.",
                "If the project uses custom security annotations, compose them with `@PreAuthorize`, `@Secured`, or related Spring Security annotations.",
                "If centralized security is the default, set `spring.correctness.linter.assumeCentralizedSecurity=true` or configure `spring.correctness.linter.securityAnnotations`."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        if (context.options().assumeCentralizedSecurity()
                || (context.options().autoDetectCentralizedSecurity() && context.hasSecurityFilterChainBean())) {
            return List.of();
        }
        List<LintIssue> issues = new ArrayList<>();
        collectIssues(sourceUnit, context, issues);
        return issues;
    }

    private void collectIssues(SourceUnit sourceUnit, ProjectContext context, List<LintIssue> issues) {
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        TypeResolutionIndex typeIndex = context.typeResolutionIndex();
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            TypeSemanticFacts typeFacts = facts.typeFacts(typeDeclaration);
            if (!typeFacts.isWebController()) {
                continue;
            }

            TypeResolutionIndex.TypeDescriptor rootType = typeIndex.descriptorFor(typeDeclaration, sourceUnit);
            SecurityInheritance securityInheritance = resolveSecurityInheritance(rootType, typeIndex, facts, context.options().customSecurityAnnotations());

            boolean classSecured = typeFacts.hasExplicitSecurityIntent()
                    || hasCustomSecurityIntent(typeFacts.annotationNames(), context.options().customSecurityAnnotations())
                    || securityInheritance.classSecured();
            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                MethodSemanticFacts methodFacts = facts.methodFacts(typeDeclaration, method);
                boolean methodSecured = methodFacts.hasExplicitSecurityIntent()
                        || hasCustomSecurityIntent(methodFacts.annotationNames(), context.options().customSecurityAnnotations());
                if (methodFacts.isPublicRequestMapping()
                        && !classSecured
                        && !methodSecured
                        && !securityInheritance.securedMethodSignatures().contains(signatureOf(method))) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Endpoint method '" + method.getNameAsString() + "' is public and mapped but has no explicit security annotation."));
                }
            }
        }
    }

    private boolean hasCustomSecurityIntent(java.util.Set<String> annotationNames, java.util.Set<String> customSecurityAnnotations) {
        if (customSecurityAnnotations.isEmpty()) {
            return false;
        }
        for (String annotation : customSecurityAnnotations) {
            if (annotationNames.contains(annotation)) {
                return true;
            }
        }
        return false;
    }

    private SecurityInheritance resolveSecurityInheritance(
            TypeResolutionIndex.TypeDescriptor rootType,
            TypeResolutionIndex typeIndex,
            SpringSemanticFacts facts,
            java.util.Set<String> customSecurityAnnotations
    ) {
        boolean inheritedClassSecured = false;
        Set<MethodSignature> securedMethods = new LinkedHashSet<>();
        for (TypeResolutionIndex.TypeDescriptor relatedType : typeIndex.relatedTypes(rootType)) {
            TypeSemanticFacts typeFacts = facts.typeFacts(relatedType.declaration());
            if (typeFacts.hasExplicitSecurityIntent()
                    || hasCustomSecurityIntent(typeFacts.annotationNames(), customSecurityAnnotations)) {
                inheritedClassSecured = true;
            }
            for (MethodDeclaration method : relatedType.structure().methodsOf(relatedType.declaration())) {
                MethodSemanticFacts methodFacts = facts.methodFacts(relatedType.declaration(), method);
                if (methodFacts.hasExplicitSecurityIntent()
                        || hasCustomSecurityIntent(methodFacts.annotationNames(), customSecurityAnnotations)) {
                    securedMethods.add(signatureOf(method));
                }
            }
        }
        return new SecurityInheritance(inheritedClassSecured, securedMethods);
    }

    private MethodSignature signatureOf(MethodDeclaration method) {
        return new MethodSignature(method.getNameAsString(), method.getParameters().size());
    }

    private record MethodSignature(String name, int arity) {
    }

    private record SecurityInheritance(boolean classSecured, Set<MethodSignature> securedMethodSignatures) {
        private SecurityInheritance {
            securedMethodSignatures = Set.copyOf(securedMethodSignatures);
        }
    }
}
