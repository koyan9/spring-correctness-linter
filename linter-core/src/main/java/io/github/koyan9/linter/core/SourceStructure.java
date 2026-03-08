/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.List;
import java.util.Map;

public record SourceStructure(
        List<TypeDeclaration<?>> typeDeclarations,
        List<MethodDeclaration> methods,
        Map<TypeDeclaration<?>, List<MethodDeclaration>> methodsByType
) {

    private static final SourceStructure EMPTY = new SourceStructure(List.of(), List.of(), Map.of());

    public SourceStructure {
        typeDeclarations = List.copyOf(typeDeclarations);
        methods = List.copyOf(methods);
        methodsByType = Map.copyOf(methodsByType);
    }

    public static SourceStructure empty() {
        return EMPTY;
    }

    public List<MethodDeclaration> methodsOf(TypeDeclaration<?> typeDeclaration) {
        return methodsByType.getOrDefault(typeDeclaration, List.of());
    }
}
