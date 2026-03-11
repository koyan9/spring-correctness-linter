/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.Set;

import static java.util.Set.of;

public record TypeSemanticFacts(
        TypeDeclaration<?> declaration,
        Set<String> annotationNames,
        boolean controller,
        boolean async,
        boolean transactional
) {

    private static final Set<String> SECURITY_ANNOTATIONS = of("PreAuthorize", "PostAuthorize", "Secured", "RolesAllowed", "PreFilter", "PostFilter", "DenyAll", "PermitAll");

    public TypeSemanticFacts {
        annotationNames = Set.copyOf(annotationNames);
    }

    public boolean hasAnnotation(String simpleName) {
        return annotationNames.contains(simpleName);
    }

    public boolean isWebController() {
        return controller;
    }

    public boolean hasAsyncBoundary() {
        return async;
    }

    public boolean hasTransactionalBoundary() {
        return transactional;
    }

    public boolean hasExplicitSecurityIntent() {
        return annotationNames.stream().anyMatch(SECURITY_ANNOTATIONS::contains);
    }

    public boolean isProfileScoped() {
        return hasAnnotation("Profile");
    }

    public boolean isProfileScopedController() {
        return isProfileScoped() && isWebController();
    }

    public boolean hasConflictingConditionalBeanAnnotations() {
        return hasAnnotation("ConditionalOnBean") && hasAnnotation("ConditionalOnMissingBean");
    }
}
