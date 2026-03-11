/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class AnnotationMetadataIndex {

    private final Map<String, List<AnnotationDefinition>> definitionsBySimpleName;

    private AnnotationMetadataIndex(Map<String, List<AnnotationDefinition>> definitionsBySimpleName) {
        this.definitionsBySimpleName = Map.copyOf(definitionsBySimpleName);
    }

    static AnnotationMetadataIndex empty() {
        return new AnnotationMetadataIndex(Map.of());
    }

    static AnnotationMetadataIndex build(List<SourceDocument> sourceDocuments) {
        Map<String, List<AnnotationDefinition>> definitionsBySimpleName = new LinkedHashMap<>();
        for (SourceDocument sourceDocument : sourceDocuments) {
            JavaSourceInspector.ParseOutcome parseOutcome = JavaSourceInspector.inspect(sourceDocument.content());
            if (parseOutcome.compilationUnit().isEmpty()) {
                continue;
            }
            CompilationUnit compilationUnit = parseOutcome.compilationUnit().get();
            for (AnnotationDeclaration declaration : compilationUnit.findAll(AnnotationDeclaration.class)) {
                AnnotationDefinition definition = new AnnotationDefinition(
                        declaration.getNameAsString(),
                        declaration.getAnnotations().stream().map(AnnotationMetadataIndex::toReference).toList(),
                        buildMemberDefinitions(declaration)
                );
                definitionsBySimpleName.computeIfAbsent(definition.simpleName(), ignored -> new ArrayList<>()).add(definition);
            }
        }
        return new AnnotationMetadataIndex(definitionsBySimpleName);
    }

    Set<String> expandedAnnotationNames(NodeWithAnnotations<?> node) {
        Set<String> names = new LinkedHashSet<>();
        for (AnnotationExpr annotationExpr : node.getAnnotations()) {
            names.addAll(expandedAnnotationNames(annotationExpr));
        }
        return names;
    }

    Set<String> expandedAnnotationNames(AnnotationExpr annotationExpr) {
        Set<String> names = new LinkedHashSet<>();
        String annotationName = JavaSourceInspector.annotationSimpleName(annotationExpr);
        names.add(annotationName);
        resolveUnique(annotationName)
                .ifPresent(definition -> definition.collectAnnotationNames(names, this, new LinkedHashSet<>()));
        return names;
    }

    Optional<String> annotationMemberValue(NodeWithAnnotations<?> node, String annotationName, String memberName) {
        for (AnnotationExpr annotationExpr : node.getAnnotations()) {
            String currentName = JavaSourceInspector.annotationSimpleName(annotationExpr);
            if (currentName.equals(annotationName)) {
                Optional<String> directValue = JavaSourceInspector.annotationMemberValue(annotationExpr, memberName);
                if (directValue.isPresent()) {
                    return directValue;
                }
            }

            Optional<AnnotationDefinition> definition = resolveUnique(currentName);
            if (definition.isPresent()) {
                Optional<String> metaValue = definition.get().findMemberValue(
                        annotationName,
                        memberName,
                        JavaSourceInspector.annotationMembers(annotationExpr),
                        this,
                        new LinkedHashSet<>()
                );
                if (metaValue.isPresent()) {
                    return metaValue;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<AnnotationDefinition> resolveUnique(String simpleName) {
        List<AnnotationDefinition> definitions = definitionsBySimpleName.getOrDefault(simpleName, List.of());
        return definitions.size() == 1 ? Optional.of(definitions.get(0)) : Optional.empty();
    }

    private static AnnotationReference toReference(AnnotationExpr annotationExpr) {
        return new AnnotationReference(
                JavaSourceInspector.annotationSimpleName(annotationExpr),
                JavaSourceInspector.annotationMembers(annotationExpr)
        );
    }

    private static Map<String, AnnotationMemberDefinition> buildMemberDefinitions(AnnotationDeclaration declaration) {
        Map<String, AnnotationMemberDefinition> members = new LinkedHashMap<>();
        for (AnnotationMemberDeclaration memberDeclaration : declaration.getMembers().stream()
                .filter(AnnotationMemberDeclaration.class::isInstance)
                .map(AnnotationMemberDeclaration.class::cast)
                .toList()) {
            members.put(
                    memberDeclaration.getNameAsString(),
                    new AnnotationMemberDefinition(
                            memberDeclaration.getNameAsString(),
                            memberDeclaration.getDefaultValue().map(Expression::toString),
                            aliasTargets(memberDeclaration)
                    )
            );
        }
        return members;
    }

    private static List<AliasTarget> aliasTargets(AnnotationMemberDeclaration memberDeclaration) {
        List<AliasTarget> targets = new ArrayList<>();
        for (AnnotationExpr annotationExpr : memberDeclaration.getAnnotations()) {
            if (!JavaSourceInspector.annotationSimpleName(annotationExpr).equals("AliasFor")) {
                continue;
            }
            Map<String, String> members = JavaSourceInspector.annotationMembers(annotationExpr);
            String targetMember = members.getOrDefault("attribute", members.get("value"));
            String annotationValue = members.get("annotation");
            if (targetMember == null || targetMember.isBlank() || annotationValue == null || annotationValue.isBlank()) {
                continue;
            }
            targets.add(new AliasTarget(annotationSimpleNameFromClassLiteral(annotationValue), stripQuotes(targetMember)));
        }
        return targets;
    }

    private static String annotationSimpleNameFromClassLiteral(String classLiteral) {
        String normalized = classLiteral.replace(".class", "").trim();
        int dotIndex = normalized.lastIndexOf('.');
        return dotIndex >= 0 ? normalized.substring(dotIndex + 1) : normalized;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record AnnotationDefinition(String simpleName, List<AnnotationReference> annotations, Map<String, AnnotationMemberDefinition> members) {

        private AnnotationDefinition {
            annotations = List.copyOf(annotations);
            members = Map.copyOf(members);
        }

        private void collectAnnotationNames(Set<String> target, AnnotationMetadataIndex index, Set<String> visited) {
            if (!visited.add(simpleName)) {
                return;
            }
            for (AnnotationReference reference : annotations) {
                target.add(reference.simpleName());
                index.resolveUnique(reference.simpleName())
                        .ifPresent(definition -> definition.collectAnnotationNames(target, index, visited));
            }
        }

        private Optional<String> findMemberValue(String annotationName, String memberName, AnnotationMetadataIndex index, Set<String> visited) {
            return findMemberValue(annotationName, memberName, Map.of(), index, visited);
        }

        private Optional<String> findMemberValue(
                String annotationName,
                String memberName,
                Map<String, String> usageMembers,
                AnnotationMetadataIndex index,
                Set<String> visited
        ) {
            if (!visited.add(simpleName)) {
                return Optional.empty();
            }
            for (AnnotationReference reference : annotations) {
                Map<String, String> effectiveMembers = resolveEffectiveMembers(reference, usageMembers);
                if (reference.simpleName().equals(annotationName) && effectiveMembers.containsKey(memberName)) {
                    return Optional.of(effectiveMembers.get(memberName));
                }
                Optional<AnnotationDefinition> nested = index.resolveUnique(reference.simpleName());
                if (nested.isPresent()) {
                    Optional<String> nestedValue = nested.get().findMemberValue(annotationName, memberName, effectiveMembers, index, visited);
                    if (nestedValue.isPresent()) {
                        return nestedValue;
                    }
                }
            }
            return Optional.empty();
        }

        private Map<String, String> resolveEffectiveMembers(AnnotationReference reference, Map<String, String> usageMembers) {
            Map<String, String> effectiveMembers = new LinkedHashMap<>(reference.members());
            for (AnnotationMemberDefinition memberDefinition : members.values()) {
                String value = usageMembers.containsKey(memberDefinition.name())
                        ? usageMembers.get(memberDefinition.name())
                        : memberDefinition.defaultValue().orElse(null);
                if (value == null) {
                    continue;
                }
                for (AliasTarget aliasTarget : memberDefinition.aliasTargets()) {
                    if (aliasTarget.annotationSimpleName().equals(reference.simpleName())) {
                        effectiveMembers.put(aliasTarget.memberName(), value);
                    }
                }
            }
            return effectiveMembers;
        }
    }

    private record AnnotationReference(String simpleName, Map<String, String> members) {

        private AnnotationReference {
            members = Map.copyOf(members);
        }
    }

    private record AnnotationMemberDefinition(String name, Optional<String> defaultValue, List<AliasTarget> aliasTargets) {

        private AnnotationMemberDefinition {
            defaultValue = defaultValue == null ? Optional.empty() : defaultValue;
            aliasTargets = List.copyOf(aliasTargets);
        }
    }

    private record AliasTarget(String annotationSimpleName, String memberName) {
    }
}
