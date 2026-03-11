/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SpringSemanticFacts {

    private final ProjectContext context;
    private final Map<Object, Set<String>> annotationNamesCache = new IdentityHashMap<>();
    private final Map<TypeDeclaration<?>, Boolean> controllerCache = new IdentityHashMap<>();
    private final Map<MethodDeclaration, Boolean> requestMappingCache = new IdentityHashMap<>();
    private final Map<TypeDeclaration<?>, TypeSemanticFacts> typeFactsCache = new IdentityHashMap<>();
    private final Map<MethodDeclaration, Map<TypeDeclaration<?>, MethodSemanticFacts>> methodFactsCache = new IdentityHashMap<>();

    private SpringSemanticFacts(ProjectContext context) {
        this.context = context;
    }

    static SpringSemanticFacts create(ProjectContext context) {
        return new SpringSemanticFacts(context);
    }

    public static SpringSemanticFacts forSourceUnit(SourceUnit sourceUnit, ProjectContext context) {
        return context.springFacts(sourceUnit);
    }

    public boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return annotationNames(node).contains(simpleName);
    }

    public Set<String> annotationNames(NodeWithAnnotations<?> node) {
        return annotationNamesCache.computeIfAbsent(node, ignored -> JavaSourceInspector.annotationNames(node, context));
    }

    public boolean annotationDeclaresMember(NodeWithAnnotations<?> node, String annotationName, String memberName) {
        return JavaSourceInspector.annotationDeclaresMember(node, context, annotationName, memberName);
    }

    public boolean annotationMemberContains(NodeWithAnnotations<?> node, String annotationName, String memberName, String token) {
        return JavaSourceInspector.annotationMemberContains(node, context, annotationName, memberName, token);
    }

    public Optional<String> annotationMemberValue(NodeWithAnnotations<?> node, String annotationName, String memberName) {
        return JavaSourceInspector.annotationMemberValue(node, context, annotationName, memberName);
    }

    public boolean isController(TypeDeclaration<?> typeDeclaration) {
        return controllerCache.computeIfAbsent(typeDeclaration, ignored -> JavaSourceInspector.isController(typeDeclaration, context));
    }

    public TypeSemanticFacts typeFacts(TypeDeclaration<?> typeDeclaration) {
        return typeFactsCache.computeIfAbsent(typeDeclaration, ignored -> new TypeSemanticFacts(
                typeDeclaration,
                annotationNames(typeDeclaration),
                isController(typeDeclaration),
                hasAnnotation(typeDeclaration, "Async"),
                hasAnnotation(typeDeclaration, "Transactional")
        ));
    }

    public boolean isRequestMapping(MethodDeclaration methodDeclaration) {
        return requestMappingCache.computeIfAbsent(methodDeclaration, ignored -> JavaSourceInspector.isRequestMapping(methodDeclaration, context));
    }

    public MethodSemanticFacts methodFacts(TypeDeclaration<?> typeDeclaration, MethodDeclaration methodDeclaration) {
        boolean cacheConfigKeyGenerator = typeDeclaration != null
                && annotationDeclaresMember(typeDeclaration, "CacheConfig", "keyGenerator");
        return methodFactsCache
                .computeIfAbsent(methodDeclaration, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(typeDeclaration, ignored -> new MethodSemanticFacts(
                        methodDeclaration,
                        annotationNames(methodDeclaration),
                        isRequestMapping(methodDeclaration),
                        hasAnnotation(methodDeclaration, "Async"),
                        hasAnnotation(methodDeclaration, "Transactional"),
                        hasAnnotation(methodDeclaration, "Scheduled"),
                        hasAnnotation(methodDeclaration, "EventListener"),
                        hasAnnotation(methodDeclaration, "TransactionalEventListener"),
                        isInitializationCallback(typeDeclaration, methodDeclaration),
                        isStartupLifecycleMethod(typeDeclaration, methodDeclaration),
                        hasAnnotation(methodDeclaration, "Cacheable")
                                && (annotationDeclaresMember(methodDeclaration, "Cacheable", "key")
                                || annotationDeclaresMember(methodDeclaration, "Cacheable", "keyGenerator")
                                || cacheConfigKeyGenerator),
                        annotationMemberContains(methodDeclaration, "Transactional", "propagation", "REQUIRES_NEW"),
                        annotationMemberContains(methodDeclaration, "Transactional", "propagation", "NESTED")
                ));
    }

    public boolean isInitializationCallback(TypeDeclaration<?> typeDeclaration, MethodDeclaration methodDeclaration) {
        return JavaSourceInspector.isInitializationCallback(typeDeclaration, methodDeclaration, context);
    }

    public boolean isStartupLifecycleMethod(TypeDeclaration<?> typeDeclaration, MethodDeclaration methodDeclaration) {
        return JavaSourceInspector.isStartupLifecycleMethod(typeDeclaration, methodDeclaration, context);
    }

    public long annotationMatchCount(NodeWithAnnotations<?> node, String simpleName) {
        return JavaSourceInspector.annotationMatchCount(node, context, simpleName);
    }
}
