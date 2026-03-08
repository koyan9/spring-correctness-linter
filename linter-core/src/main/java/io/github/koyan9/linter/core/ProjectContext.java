/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProjectContext {

    private final Path projectRoot;
    private final Path sourceDirectory;
    private final List<SourceRoot> sourceRoots;
    private final List<SourceDocument> sourceDocuments;

    private ProjectContext(Path projectRoot, Path sourceDirectory, List<SourceRoot> sourceRoots, List<SourceDocument> sourceDocuments) {
        this.projectRoot = projectRoot;
        this.sourceDirectory = sourceDirectory;
        this.sourceRoots = List.copyOf(sourceRoots);
        this.sourceDocuments = List.copyOf(sourceDocuments);
    }

    public static ProjectContext load(Path projectRoot, Path sourceDirectory) throws IOException {
        return loadSourceRoots(projectRoot, List.of(SourceRoot.of(projectRoot, sourceDirectory)));
    }

    public static ProjectContext load(Path projectRoot, List<Path> sourceDirectories) throws IOException {
        return loadSourceRoots(projectRoot, sourceDirectories.stream().map(path -> SourceRoot.of(projectRoot, path)).toList());
    }

    public static ProjectContext loadSourceRoots(Path projectRoot, List<SourceRoot> sourceRoots) throws IOException {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        List<SourceRoot> normalizedSourceRoots = sourceRoots.stream()
                .map(sourceRoot -> new SourceRoot(sourceRoot.path().toAbsolutePath().normalize(), sourceRoot.moduleId()))
                .distinct()
                .toList();
        Path primarySourceDirectory = normalizedSourceRoots.isEmpty() ? normalizedRoot : normalizedSourceRoots.get(0).path();

        if (normalizedSourceRoots.isEmpty()) {
            return new ProjectContext(normalizedRoot, primarySourceDirectory, List.of(), List.of());
        }

        Map<Path, SourceDocument> documentsByPath = new LinkedHashMap<>();
        for (SourceRoot sourceRoot : normalizedSourceRoots) {
            Path normalizedSource = sourceRoot.path();
            if (!Files.exists(normalizedSource)) {
                continue;
            }

            try (Stream<Path> stream = Files.walk(normalizedSource)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(path -> readDocument(path, sourceRoot.moduleId()))
                        .forEach(document -> documentsByPath.put(document.path(), document));
            }
        }

        return new ProjectContext(
                normalizedRoot,
                primarySourceDirectory,
                normalizedSourceRoots,
                documentsByPath.values().stream().collect(Collectors.toList())
        );
    }

    private static SourceDocument readDocument(Path path, String moduleId) {
        try {
            return new SourceDocument(path.toAbsolutePath().normalize(), Files.readString(path), null, moduleId);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read source file: " + path, exception);
        }
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path sourceDirectory() {
        return sourceDirectory;
    }

    public List<Path> sourceDirectories() {
        return sourceRoots.stream().map(SourceRoot::path).toList();
    }

    public List<SourceRoot> sourceRoots() {
        return sourceRoots;
    }

    public List<SourceDocument> sourceDocuments() {
        return sourceDocuments;
    }

    public List<SourceUnit> sourceUnits() {
        return sourceDocuments.stream().map(SourceDocument::toSourceUnit).toList();
    }
}
