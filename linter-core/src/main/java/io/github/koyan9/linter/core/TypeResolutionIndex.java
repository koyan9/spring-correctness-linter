/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TypeResolutionIndex {

    private final Map<String, TypeDescriptor> byFqn;
    private final Map<String, List<TypeDescriptor>> bySimple;

    private TypeResolutionIndex(Map<String, TypeDescriptor> byFqn, Map<String, List<TypeDescriptor>> bySimple) {
        this.byFqn = Map.copyOf(byFqn);
        Map<String, List<TypeDescriptor>> copied = new HashMap<>();
        for (Map.Entry<String, List<TypeDescriptor>> entry : bySimple.entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.bySimple = Map.copyOf(copied);
    }

    public static TypeResolutionIndex build(ProjectContext context) {
        Map<String, TypeDescriptor> byFqn = new HashMap<>();
        Map<String, List<TypeDescriptor>> bySimple = new HashMap<>();
        for (SourceUnit sourceUnit : context.sourceUnits()) {
            String packageName = packageName(sourceUnit);
            ImportInfo importInfo = importInfo(sourceUnit);
            for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
                String simpleName = typeDeclaration.getNameAsString();
                if (simpleName.isBlank()) {
                    continue;
                }
                String qualifiedName = qualifiedNameOf(typeDeclaration, packageName);
                TypeDescriptor descriptor = new TypeDescriptor(
                        qualifiedName,
                        packageName,
                        typeDeclaration,
                        sourceUnit.structure(),
                        importInfo
                );
                byFqn.putIfAbsent(qualifiedName, descriptor);
                bySimple.computeIfAbsent(simpleName, ignored -> new ArrayList<>()).add(descriptor);
            }
        }
        return new TypeResolutionIndex(byFqn, bySimple);
    }

    public TypeDescriptor descriptorFor(TypeDeclaration<?> typeDeclaration, SourceUnit sourceUnit) {
        String packageName = packageName(sourceUnit);
        ImportInfo importInfo = importInfo(sourceUnit);
        String qualifiedName = qualifiedNameOf(typeDeclaration, packageName);
        return byFqn.getOrDefault(
                qualifiedName,
                new TypeDescriptor(qualifiedName, packageName, typeDeclaration, sourceUnit.structure(), importInfo)
        );
    }

    public Set<TypeDescriptor> relatedTypes(TypeDescriptor rootType) {
        Set<TypeDescriptor> relatedTypes = new LinkedHashSet<>();
        ArrayDeque<TypeDescriptor> queue = new ArrayDeque<>();
        relatedTypes.add(rootType);
        queue.add(rootType);
        while (!queue.isEmpty()) {
            TypeDescriptor current = queue.removeFirst();
            if (!(current.declaration() instanceof ClassOrInterfaceDeclaration declaration)) {
                continue;
            }
            String packageName = current.packageName();
            for (ClassOrInterfaceType extendedType : declaration.getExtendedTypes()) {
                resolveType(extendedType, current, packageName)
                        .filter(relatedTypes::add)
                        .ifPresent(queue::add);
            }
            for (ClassOrInterfaceType implementedType : declaration.getImplementedTypes()) {
                resolveType(implementedType, current, packageName)
                        .filter(relatedTypes::add)
                        .ifPresent(queue::add);
            }
        }
        return relatedTypes;
    }

    private Optional<TypeDescriptor> resolveType(
            ClassOrInterfaceType type,
            TypeDescriptor contextType,
            String currentPackage
    ) {
        String rawName = stripGenerics(type.toString());
        if (rawName.contains(".")) {
            TypeDescriptor qualified = byFqn.get(rawName);
            if (qualified != null) {
                return Optional.of(qualified);
            }
            if (!currentPackage.isBlank()) {
                TypeDescriptor inPackage = byFqn.get(currentPackage + "." + rawName);
                if (inPackage != null) {
                    return Optional.of(inPackage);
                }
            }
            int dotIndex = rawName.indexOf('.');
            String outerSimple = rawName.substring(0, dotIndex);
            String nestedSuffix = rawName.substring(dotIndex + 1);
            String explicitOuter = contextType.importInfo().explicitImports().get(outerSimple);
            if (explicitOuter != null) {
                TypeDescriptor nested = byFqn.get(explicitOuter + "." + nestedSuffix);
                if (nested != null) {
                    return Optional.of(nested);
                }
            }
            TypeDescriptor wildcardNestedMatch = null;
            for (String wildcardPackage : contextType.importInfo().wildcardImports()) {
                TypeDescriptor candidate = byFqn.get(wildcardPackage + "." + rawName);
                if (candidate == null) {
                    continue;
                }
                if (wildcardNestedMatch != null) {
                    return Optional.empty();
                }
                wildcardNestedMatch = candidate;
            }
            return Optional.ofNullable(wildcardNestedMatch);
        }
        if (!currentPackage.isBlank()) {
            TypeDescriptor inPackage = byFqn.get(currentPackage + "." + rawName);
            if (inPackage != null) {
                return Optional.of(inPackage);
            }
        }
        String explicitImport = contextType.importInfo().explicitImports().get(rawName);
        if (explicitImport != null) {
            TypeDescriptor explicitMatch = byFqn.get(explicitImport);
            if (explicitMatch != null) {
                return Optional.of(explicitMatch);
            }
        }
        TypeDescriptor wildcardMatch = null;
        for (String wildcardPackage : contextType.importInfo().wildcardImports()) {
            TypeDescriptor candidate = byFqn.get(wildcardPackage + "." + rawName);
            if (candidate == null) {
                continue;
            }
            if (wildcardMatch != null) {
                return Optional.empty();
            }
            wildcardMatch = candidate;
        }
        return Optional.ofNullable(wildcardMatch);
    }

    private static String packageName(SourceUnit sourceUnit) {
        return sourceUnit.compilationUnit()
                .flatMap(unit -> unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()))
                .orElse("");
    }

    private static ImportInfo importInfo(SourceUnit sourceUnit) {
        Map<String, String> explicitImports = new HashMap<>();
        Set<String> wildcardImports = new LinkedHashSet<>();
        sourceUnit.compilationUnit().ifPresent(unit -> {
            for (ImportDeclaration importDeclaration : unit.getImports()) {
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
        });
        return new ImportInfo(explicitImports, wildcardImports);
    }

    private static String stripGenerics(String typeName) {
        int genericStart = typeName.indexOf('<');
        return genericStart < 0 ? typeName : typeName.substring(0, genericStart);
    }

    private static String qualifiedNameOf(TypeDeclaration<?> typeDeclaration, String packageName) {
        List<String> segments = new ArrayList<>();
        segments.add(typeDeclaration.getNameAsString());
        Optional<Node> parent = typeDeclaration.getParentNode();
        while (parent.isPresent()) {
            Node node = parent.get();
            if (node instanceof TypeDeclaration<?> parentType) {
                segments.add(parentType.getNameAsString());
            }
            parent = node.getParentNode();
        }
        Collections.reverse(segments);
        String localName = String.join(".", segments);
        return packageName.isBlank() ? localName : packageName + "." + localName;
    }

    public record TypeDescriptor(
            String qualifiedName,
            String packageName,
            TypeDeclaration<?> declaration,
            SourceStructure structure,
            ImportInfo importInfo
    ) {
    }

    public record ImportInfo(Map<String, String> explicitImports, Set<String> wildcardImports) {
        public ImportInfo {
            explicitImports = Map.copyOf(explicitImports);
            wildcardImports = Set.copyOf(wildcardImports);
        }
    }
}
