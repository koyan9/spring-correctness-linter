/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.Set;

import static java.util.Set.of;

public record MethodSemanticFacts(
        MethodDeclaration declaration,
        Set<String> annotationNames,
        boolean requestMapping,
        boolean async,
        boolean transactional,
        boolean scheduled,
        boolean eventListener,
        boolean transactionalEventListener,
        boolean initializationCallback,
        boolean startupLifecycleMethod,
        boolean cacheableOperation,
        boolean explicitCacheKeyStrategy,
        boolean transactionRequiresNew,
        boolean transactionNested
) {

    private static final Set<String> SECURITY_ANNOTATIONS = of("PreAuthorize", "PostAuthorize", "Secured", "RolesAllowed", "PreFilter", "PostFilter", "DenyAll", "PermitAll");

    public MethodSemanticFacts {
        annotationNames = Set.copyOf(annotationNames);
    }

    public boolean hasAnnotation(String simpleName) {
        return annotationNames.contains(simpleName);
    }

    public boolean hasAsyncBoundary() {
        return async;
    }

    public boolean hasTransactionalBoundary() {
        return transactional;
    }

    public boolean hasSchedulingBoundary() {
        return scheduled;
    }

    public boolean hasEventListenerBoundary() {
        return eventListener;
    }

    public boolean hasTransactionalEventListenerBoundary() {
        return transactionalEventListener;
    }

    public boolean hasExplicitSecurityIntent() {
        return annotationNames.stream().anyMatch(SECURITY_ANNOTATIONS::contains);
    }

    public boolean isPublicRequestMapping() {
        return requestMapping && declaration.isPublic();
    }

    public boolean isLifecycleBoundary() {
        return initializationCallback || startupLifecycleMethod;
    }

    public boolean hasTransactionalEventBoundaryConflict() {
        return transactionalEventListener && transactional;
    }

    public boolean isTransactionalPrivateMethod() {
        return transactional && declaration.isPrivate();
    }

    public boolean isTransactionalFinalMethod() {
        return transactional && declaration.isFinal();
    }

    public boolean isAsyncPrivateMethod() {
        return async && declaration.isPrivate();
    }

    public boolean isAsyncVoidMethod() {
        return async && declaration.getType().isVoidType();
    }

    public boolean isScheduledMethodWithParameters() {
        return scheduled && !declaration.getParameters().isEmpty();
    }

    public boolean hasConflictingCacheAnnotations() {
        int count = (hasAnnotation("Cacheable") ? 1 : 0)
                + (hasAnnotation("CachePut") ? 1 : 0)
                + (hasAnnotation("CacheEvict") ? 1 : 0);
        return count >= 2;
    }

    public boolean isScheduledAsyncBoundary() {
        return scheduled && async;
    }

    public boolean isScheduledTransactionalBoundary(boolean classTransactional) {
        return scheduled && (classTransactional || transactional);
    }

    public boolean isEventListenerTransactionalBoundary(boolean classTransactional) {
        return eventListener && !transactionalEventListener && (classTransactional || transactional);
    }

    public boolean hasConflictingConditionalBeanAnnotations() {
        return hasAnnotation("ConditionalOnBean") && hasAnnotation("ConditionalOnMissingBean");
    }

    public boolean hasExplicitCacheKeyStrategy() {
        return explicitCacheKeyStrategy;
    }

    public boolean hasCacheableOperation() {
        return cacheableOperation;
    }

    public boolean shouldDeclareExplicitCacheKey() {
        return cacheableOperation
                && !explicitCacheKeyStrategy
                && !declaration.getParameters().isEmpty();
    }

    public boolean hasHighRiskTransactionPropagation() {
        return transactionRequiresNew || transactionNested;
    }

    public String highRiskTransactionPropagationName() {
        if (transactionRequiresNew) {
            return "REQUIRES_NEW";
        }
        if (transactionNested) {
            return "NESTED";
        }
        return "";
    }
}
