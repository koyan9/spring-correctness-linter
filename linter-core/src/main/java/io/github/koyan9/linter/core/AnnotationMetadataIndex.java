/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
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
    private final Map<String, AnnotationDefinition> definitionsByQualifiedName;

    private AnnotationMetadataIndex(
            Map<String, List<AnnotationDefinition>> definitionsBySimpleName,
            Map<String, AnnotationDefinition> definitionsByQualifiedName
    ) {
        this.definitionsBySimpleName = Map.copyOf(definitionsBySimpleName);
        this.definitionsByQualifiedName = Map.copyOf(definitionsByQualifiedName);
    }

    static AnnotationMetadataIndex empty() {
        return new AnnotationMetadataIndex(Map.of(), Map.of());
    }

    static AnnotationMetadataIndex build(List<SourceDocument> sourceDocuments) {
        Map<String, List<AnnotationDefinition>> definitionsBySimpleName = new LinkedHashMap<>();
        Map<String, AnnotationDefinition> definitionsByQualifiedName = new LinkedHashMap<>();
        for (SourceDocument sourceDocument : sourceDocuments) {
            JavaSourceInspector.ParseOutcome parseOutcome = JavaSourceInspector.inspect(sourceDocument.content());
            if (parseOutcome.compilationUnit().isEmpty()) {
                continue;
            }
            CompilationUnit compilationUnit = parseOutcome.compilationUnit().get();
            String packageName = compilationUnit.getPackageDeclaration()
                    .map(declaration -> declaration.getNameAsString())
                    .orElse("");
            AnnotationContext context = annotationContext(compilationUnit, packageName);
            for (AnnotationDeclaration declaration : compilationUnit.findAll(AnnotationDeclaration.class)) {
                String qualifiedName = qualifiedNameOf(declaration, packageName);
                AnnotationDefinition definition = new AnnotationDefinition(
                        declaration.getNameAsString(),
                        qualifiedName,
                        packageName,
                        declaration.getAnnotations().stream().map(AnnotationMetadataIndex::toReference).toList(),
                        buildMemberDefinitions(declaration),
                        context
                );
                definitionsBySimpleName.computeIfAbsent(definition.simpleName(), ignored -> new ArrayList<>()).add(definition);
                definitionsByQualifiedName.putIfAbsent(qualifiedName, definition);
            }
        }
        return new AnnotationMetadataIndex(definitionsBySimpleName, definitionsByQualifiedName);
    }

    Set<String> expandedAnnotationNames(NodeWithAnnotations<?> node) {
        Set<String> names = new LinkedHashSet<>();
        AnnotationContext context = annotationContext(extractCompilationUnit(node));
        for (AnnotationExpr annotationExpr : node.getAnnotations()) {
            names.addAll(expandedAnnotationNames(annotationExpr, context));
        }
        return names;
    }

    Set<String> expandedAnnotationNames(AnnotationExpr annotationExpr) {
        AnnotationContext context = annotationContext(annotationExpr.findCompilationUnit().orElse(null));
        return expandedAnnotationNames(annotationExpr, context);
    }

    Optional<String> annotationMemberValue(NodeWithAnnotations<?> node, String annotationName, String memberName) {
        AnnotationContext context = annotationContext(extractCompilationUnit(node));
        for (AnnotationExpr annotationExpr : node.getAnnotations()) {
            String currentName = JavaSourceInspector.annotationSimpleName(annotationExpr);
            if (currentName.equals(annotationName)) {
                Optional<String> directValue = JavaSourceInspector.annotationMemberValue(annotationExpr, memberName);
                if (directValue.isPresent()) {
                    return directValue;
                }
            }

            Optional<AnnotationDefinition> definition = resolveDefinition(annotationExpr.getNameAsString(), context);
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

    private Set<String> expandedAnnotationNames(AnnotationExpr annotationExpr, AnnotationContext context) {
        Set<String> names = new LinkedHashSet<>();
        String annotationName = annotationExpr.getNameAsString();
        names.add(simpleNameOf(annotationName));
        resolveDefinition(annotationName, context)
                .ifPresent(definition -> definition.collectAnnotationNames(names, this, new LinkedHashSet<>()));
        return names;
    }

    private Optional<AnnotationDefinition> resolveDefinition(String annotationName, AnnotationContext context) {
        if (annotationName == null || annotationName.isBlank()) {
            return Optional.empty();
        }
        String simpleName = simpleNameOf(annotationName);
        if (annotationName.contains(".")) {
            AnnotationDefinition qualified = definitionsByQualifiedName.get(annotationName);
            if (qualified != null) {
                return Optional.of(qualified);
            }
            if (context != null && !context.packageName().isBlank()) {
                AnnotationDefinition inPackage = definitionsByQualifiedName.get(context.packageName() + "." + annotationName);
                if (inPackage != null) {
                    return Optional.of(inPackage);
                }
            }
            int dotIndex = annotationName.indexOf('.');
            String outerSimple = annotationName.substring(0, dotIndex);
            String nestedSuffix = annotationName.substring(dotIndex + 1);
            if (context != null) {
                String explicitOuter = context.explicitImports().get(outerSimple);
                if (explicitOuter != null) {
                    AnnotationDefinition nested = definitionsByQualifiedName.get(explicitOuter + "." + nestedSuffix);
                    if (nested != null) {
                        return Optional.of(nested);
                    }
                }
                AnnotationDefinition wildcardNestedMatch = null;
                for (String wildcardPackage : context.wildcardImports()) {
                    AnnotationDefinition candidate = definitionsByQualifiedName.get(wildcardPackage + "." + annotationName);
                    if (candidate == null) {
                        continue;
                    }
                    if (wildcardNestedMatch != null) {
                        return Optional.empty();
                    }
                    wildcardNestedMatch = candidate;
                }
                if (wildcardNestedMatch != null) {
                    return Optional.of(wildcardNestedMatch);
                }
            }
        } else if (context != null) {
            if (!context.packageName().isBlank()) {
                AnnotationDefinition inPackage = definitionsByQualifiedName.get(context.packageName() + "." + annotationName);
                if (inPackage != null) {
                    return Optional.of(inPackage);
                }
            }
            String explicitImport = context.explicitImports().get(annotationName);
            if (explicitImport != null) {
                AnnotationDefinition explicitMatch = definitionsByQualifiedName.get(explicitImport);
                if (explicitMatch != null) {
                    return Optional.of(explicitMatch);
                }
            }
            AnnotationDefinition wildcardMatch = null;
            for (String wildcardPackage : context.wildcardImports()) {
                AnnotationDefinition candidate = definitionsByQualifiedName.get(wildcardPackage + "." + annotationName);
                if (candidate == null) {
                    continue;
                }
                if (wildcardMatch != null) {
                    return Optional.empty();
                }
                wildcardMatch = candidate;
            }
            if (wildcardMatch != null) {
                return Optional.of(wildcardMatch);
            }
        }

        List<AnnotationDefinition> definitions = definitionsBySimpleName.getOrDefault(simpleName, List.of());
        return definitions.size() == 1 ? Optional.of(definitions.get(0)) : Optional.empty();
    }

    private static AnnotationReference toReference(AnnotationExpr annotationExpr) {
        return new AnnotationReference(
                annotationExpr.getNameAsString(),
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

    private record AnnotationDefinition(
            String simpleName,
            String qualifiedName,
            String packageName,
            List<AnnotationReference> annotations,
            Map<String, AnnotationMemberDefinition> members,
            AnnotationContext context
    ) {

        private AnnotationDefinition {
            annotations = List.copyOf(annotations);
            members = Map.copyOf(members);
        }

        private void collectAnnotationNames(Set<String> target, AnnotationMetadataIndex index, Set<String> visited) {
            if (!visited.add(qualifiedName)) {
                return;
            }
            for (AnnotationReference reference : annotations) {
                target.add(simpleNameOf(reference.name()));
                index.resolveDefinition(reference.name(), context)
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
                Optional<AnnotationDefinition> resolvedDefinition = index.resolveDefinition(reference.name(), context);
                String resolvedSimpleName = resolvedDefinition.map(AnnotationDefinition::simpleName)
                        .orElse(simpleNameOf(reference.name()));
                Map<String, String> effectiveMembers = resolveEffectiveMembers(reference, usageMembers);
                if (resolvedSimpleName.equals(annotationName) && effectiveMembers.containsKey(memberName)) {
                    return Optional.of(effectiveMembers.get(memberName));
                }
                if (resolvedDefinition.isPresent()) {
                    Optional<String> nestedValue = resolvedDefinition.get().findMemberValue(annotationName, memberName, effectiveMembers, index, visited);
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
                    if (aliasTarget.annotationSimpleName().equals(simpleNameOf(reference.name()))) {
                        effectiveMembers.put(aliasTarget.memberName(), value);
                    }
                }
            }
            return effectiveMembers;
        }
    }

    private record AnnotationReference(String name, Map<String, String> members) {

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

    private record AnnotationContext(String packageName, Map<String, String> explicitImports, Set<String> wildcardImports) {
        private AnnotationContext {
            explicitImports = Map.copyOf(explicitImports);
            wildcardImports = Set.copyOf(wildcardImports);
        }
    }

    private static AnnotationContext annotationContext(CompilationUnit compilationUnit, String packageName) {
        Map<String, String> explicitImports = new LinkedHashMap<>();
        Set<String> wildcardImports = new LinkedHashSet<>();
        if (compilationUnit != null) {
            for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
                if (importDeclaration.isStatic()) {
                    continue;
                }
                String name = importDeclaration.getNameAsString();
                if (importDeclaration.isAsterisk()) {
                    wildcardImports.add(name);
                    continue;
                }
                int lastDot = name.lastIndexOf('.');
                if (lastDot >= 0 && lastDot < name.length() - 1) {
                    explicitImports.putIfAbsent(name.substring(lastDot + 1), name);
                }
            }
        }
        return new AnnotationContext(packageName == null ? "" : packageName, explicitImports, wildcardImports);
    }

    private static AnnotationContext annotationContext(CompilationUnit compilationUnit) {
        if (compilationUnit == null) {
            return new AnnotationContext("", Map.of(), Set.of());
        }
        String packageName = compilationUnit.getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString())
                .orElse("");
        return annotationContext(compilationUnit, packageName);
    }

    private static CompilationUnit extractCompilationUnit(NodeWithAnnotations<?> node) {
        if (node instanceof Node nodeRef) {
            return nodeRef.findCompilationUnit().orElse(null);
        }
        return null;
    }

    private static String qualifiedNameOf(AnnotationDeclaration declaration, String packageName) {
        List<String> segments = new ArrayList<>();
        segments.add(declaration.getNameAsString());
        Optional<Node> parent = declaration.getParentNode();
        while (parent.isPresent()) {
            Node node = parent.get();
            if (node instanceof AnnotationDeclaration parentAnnotation) {
                segments.add(parentAnnotation.getNameAsString());
            } else if (node instanceof com.github.javaparser.ast.body.TypeDeclaration<?> parentType) {
                segments.add(parentType.getNameAsString());
            }
            parent = node.getParentNode();
        }
        java.util.Collections.reverse(segments);
        String localName = String.join(".", segments);
        return packageName == null || packageName.isBlank() ? localName : packageName + "." + localName;
    }

    private static String simpleNameOf(String name) {
        if (name == null) {
            return "";
        }
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 ? name.substring(dotIndex + 1) : name;
    }
}
