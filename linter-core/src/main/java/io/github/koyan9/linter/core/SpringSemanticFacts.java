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

    public boolean annotationMemberIsEmpty(NodeWithAnnotations<?> node, String annotationName, String memberName) {
        for (com.github.javaparser.ast.expr.AnnotationExpr annotationExpr : node.getAnnotations()) {
            if (JavaSourceInspector.annotationSimpleName(annotationExpr).equals(annotationName)
                    && JavaSourceInspector.annotationMemberIsEmpty(annotationExpr, memberName)) {
                return true;
            }
        }
        return context.annotationMetadataIndex()
                .annotationMemberValue(node, annotationName, memberName)
                .map(JavaSourceInspector::isBlankLiteral)
                .orElse(false);
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
                && annotationDeclaresMember(typeDeclaration, "CacheConfig", "keyGenerator")
                && !annotationMemberIsEmpty(typeDeclaration, "CacheConfig", "keyGenerator");
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
                                && ((annotationDeclaresMember(methodDeclaration, "Cacheable", "key")
                                && !annotationMemberIsEmpty(methodDeclaration, "Cacheable", "key"))
                                || (annotationDeclaresMember(methodDeclaration, "Cacheable", "keyGenerator")
                                && !annotationMemberIsEmpty(methodDeclaration, "Cacheable", "keyGenerator"))
                                || cacheConfigKeyGenerator),
                        annotationMemberContains(methodDeclaration, "Transactional", "propagation", "REQUIRES_NEW"),
                        annotationMemberContains(methodDeclaration, "Transactional", "propagation", "NESTED")
                ));
    }

    public ScheduledTriggerSummary scheduledTriggerSummary(MethodDeclaration methodDeclaration) {
        TriggerValue cron = stringTriggerValue(methodDeclaration, "cron");
        TriggerValue fixedDelay = merge(
                numericTriggerValue(methodDeclaration, "fixedDelay"),
                stringTriggerValue(methodDeclaration, "fixedDelayString")
        );
        TriggerValue fixedRate = merge(
                numericTriggerValue(methodDeclaration, "fixedRate"),
                stringTriggerValue(methodDeclaration, "fixedRateString")
        );
        TriggerValue initialDelay = merge(
                numericTriggerValue(methodDeclaration, "initialDelay"),
                stringTriggerValue(methodDeclaration, "initialDelayString")
        );
        return new ScheduledTriggerSummary(cron, fixedDelay, fixedRate, initialDelay);
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

    private TriggerValue stringTriggerValue(MethodDeclaration methodDeclaration, String memberName) {
        return annotationMemberValue(methodDeclaration, "Scheduled", memberName)
                .map(this::parseStringTriggerValue)
                .orElse(TriggerValue.missingValue());
    }

    private TriggerValue numericTriggerValue(MethodDeclaration methodDeclaration, String memberName) {
        return annotationMemberValue(methodDeclaration, "Scheduled", memberName)
                .map(this::parseNumericTriggerValue)
                .orElse(TriggerValue.missingValue());
    }

    private TriggerValue parseStringTriggerValue(String rawValue) {
        String trimmed = rawValue.trim();
        if (trimmed.isBlank()) {
            return TriggerValue.missingValue();
        }
        boolean quoted = trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"");
        String normalized = quoted ? trimmed.substring(1, trimmed.length() - 1).trim() : trimmed;
        if (normalized.isBlank()) {
            return TriggerValue.missingValue();
        }
        if (!quoted || isPlaceholderValue(normalized)) {
            return TriggerValue.placeholderValue();
        }
        return TriggerValue.literalValue();
    }

    private TriggerValue parseNumericTriggerValue(String rawValue) {
        String normalized = normalizeNumericLiteral(rawValue);
        if (normalized.isBlank()) {
            return TriggerValue.missingValue();
        }
        if (isPlaceholderValue(normalized)) {
            return TriggerValue.placeholderValue();
        }
        try {
            long parsed = Long.parseLong(normalized);
            if (parsed == -1) {
                return TriggerValue.missingValue();
            }
        } catch (NumberFormatException exception) {
            return TriggerValue.placeholderValue();
        }
        return TriggerValue.literalValue();
    }

    private boolean isPlaceholderValue(String value) {
        return value.contains("${") || value.contains("#{");
    }

    private String normalizeNumericLiteral(String value) {
        String normalized = value.trim().replace("_", "");
        if (normalized.endsWith("L") || normalized.endsWith("l")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private TriggerValue merge(TriggerValue left, TriggerValue right) {
        if (left.configured || right.configured) {
            if (left.placeholder || right.placeholder) {
                return TriggerValue.placeholderValue();
            }
            return TriggerValue.literalValue();
        }
        return TriggerValue.missingValue();
    }

    public record ScheduledTriggerSummary(
            TriggerValue cron,
            TriggerValue fixedDelay,
            TriggerValue fixedRate,
            TriggerValue initialDelay
    ) {

        public int periodicConfiguredCount() {
            return (cron.configured ? 1 : 0) + (fixedDelay.configured ? 1 : 0) + (fixedRate.configured ? 1 : 0);
        }

        public int periodicLiteralCount() {
            return (cron.literal ? 1 : 0) + (fixedDelay.literal ? 1 : 0) + (fixedRate.literal ? 1 : 0);
        }

        public boolean periodicHasPlaceholder() {
            return cron.placeholder || fixedDelay.placeholder || fixedRate.placeholder;
        }

        public boolean initialDelayConfigured() {
            return initialDelay.configured;
        }
    }

    public record TriggerValue(boolean configured, boolean literal, boolean placeholder) {
        public static TriggerValue missingValue() {
            return new TriggerValue(false, false, false);
        }

        public static TriggerValue literalValue() {
            return new TriggerValue(true, true, false);
        }

        public static TriggerValue placeholderValue() {
            return new TriggerValue(true, false, true);
        }
    }
}
