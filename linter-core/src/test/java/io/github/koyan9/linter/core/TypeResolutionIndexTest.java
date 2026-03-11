/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeResolutionIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesSamePackageBeforeAmbiguousSimpleName() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Path moduleB = tempDir.resolve("module-b/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("demo"));
        Files.createDirectories(moduleB.resolve("other"));
        Files.writeString(moduleA.resolve("demo/BaseService.java"), """
                package demo;

                class BaseService {
                }
                """);
        Files.writeString(moduleB.resolve("other/BaseService.java"), """
                package other;

                class BaseService {
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                class ChildService extends BaseService {
                }
                """);

        ProjectContext context = ProjectContext.load(tempDir, List.of(rootApp, moduleA, moduleB));
        TypeResolutionIndex index = context.typeResolutionIndex();
        TypeResolutionIndex.TypeDescriptor rootType = descriptorFor(context, index, "ChildService.java");
        Set<String> related = index.relatedTypes(rootType).stream()
                .map(TypeResolutionIndex.TypeDescriptor::qualifiedName)
                .collect(Collectors.toSet());

        assertTrue(related.contains("demo.BaseService"));
        assertFalse(related.contains("other.BaseService"));
    }

    @Test
    void resolvesWildcardImportAcrossPackages() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("other"));
        Files.writeString(moduleA.resolve("other/BaseService.java"), """
                package other;

                class BaseService {
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                import other.*;

                class ChildService extends BaseService {
                }
                """);

        ProjectContext context = ProjectContext.load(tempDir, List.of(rootApp, moduleA));
        TypeResolutionIndex index = context.typeResolutionIndex();
        TypeResolutionIndex.TypeDescriptor rootType = descriptorFor(context, index, "ChildService.java");
        Set<String> related = index.relatedTypes(rootType).stream()
                .map(TypeResolutionIndex.TypeDescriptor::qualifiedName)
                .collect(Collectors.toSet());

        assertTrue(related.contains("other.BaseService"));
    }

    @Test
    void resolvesExplicitNestedTypeImportAcrossPackages() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("other"));
        Files.writeString(moduleA.resolve("other/Outer.java"), """
                package other;

                class Outer {

                    static class BaseService {
                    }
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                import other.Outer.BaseService;

                class ChildService extends BaseService {
                }
                """);

        ProjectContext context = ProjectContext.load(tempDir, List.of(rootApp, moduleA));
        TypeResolutionIndex index = context.typeResolutionIndex();
        TypeResolutionIndex.TypeDescriptor rootType = descriptorFor(context, index, "ChildService.java");
        Set<String> related = index.relatedTypes(rootType).stream()
                .map(TypeResolutionIndex.TypeDescriptor::qualifiedName)
                .collect(Collectors.toSet());

        assertTrue(related.contains("other.Outer.BaseService"));
    }

    @Test
    void rejectsAmbiguousWildcardNestedType() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Path moduleB = tempDir.resolve("module-b/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("other"));
        Files.createDirectories(moduleB.resolve("another"));
        Files.writeString(moduleA.resolve("other/Outer.java"), """
                package other;

                class Outer {

                    static class BaseService {
                    }
                }
                """);
        Files.writeString(moduleB.resolve("another/Outer.java"), """
                package another;

                class Outer {

                    static class BaseService {
                    }
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                import other.*;
                import another.*;

                class ChildService extends Outer.BaseService {
                }
                """);

        ProjectContext context = ProjectContext.load(tempDir, List.of(rootApp, moduleA, moduleB));
        TypeResolutionIndex index = context.typeResolutionIndex();
        TypeResolutionIndex.TypeDescriptor rootType = descriptorFor(context, index, "ChildService.java");
        Set<String> related = index.relatedTypes(rootType).stream()
                .map(TypeResolutionIndex.TypeDescriptor::qualifiedName)
                .collect(Collectors.toSet());

        assertFalse(related.contains("other.Outer.BaseService"));
        assertFalse(related.contains("another.Outer.BaseService"));
    }

    private TypeResolutionIndex.TypeDescriptor descriptorFor(
            ProjectContext context,
            TypeResolutionIndex index,
            String filename
    ) {
        SourceUnit sourceUnit = context.sourceUnits().stream()
                .filter(unit -> unit.path().getFileName().toString().equals(filename))
                .findFirst()
                .orElseThrow();
        TypeDeclaration<?> typeDeclaration = sourceUnit.structure().typeDeclarations().get(0);
        return index.descriptorFor(typeDeclaration, sourceUnit);
    }
}
