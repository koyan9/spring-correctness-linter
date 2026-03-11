/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
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
    private final AnnotationMetadataIndex annotationMetadataIndex;
    private final LintOptions options;
    private final Map<SourceUnit, SpringSemanticFacts> semanticFactsBySourceUnit = new IdentityHashMap<>();
    private volatile TypeResolutionIndex typeResolutionIndex;

    private ProjectContext(
            Path projectRoot,
            Path sourceDirectory,
            List<SourceRoot> sourceRoots,
            List<SourceDocument> sourceDocuments,
            AnnotationMetadataIndex annotationMetadataIndex,
            LintOptions options
    ) {
        this.projectRoot = projectRoot;
        this.sourceDirectory = sourceDirectory;
        this.sourceRoots = List.copyOf(sourceRoots);
        this.sourceDocuments = List.copyOf(sourceDocuments);
        this.annotationMetadataIndex = annotationMetadataIndex;
        this.options = options == null ? LintOptions.defaults() : options;
    }

    public static ProjectContext load(Path projectRoot, Path sourceDirectory) throws IOException {
        return loadSourceRoots(projectRoot, List.of(SourceRoot.of(projectRoot, sourceDirectory)), LintOptions.defaults());
    }

    public static ProjectContext load(Path projectRoot, List<Path> sourceDirectories) throws IOException {
        return loadSourceRoots(projectRoot, sourceDirectories.stream().map(path -> SourceRoot.of(projectRoot, path)).toList(), LintOptions.defaults());
    }

    public static ProjectContext loadSourceRoots(Path projectRoot, List<SourceRoot> sourceRoots) throws IOException {
        return loadSourceRoots(projectRoot, sourceRoots, LintOptions.defaults());
    }

    public static ProjectContext loadSourceRoots(Path projectRoot, List<SourceRoot> sourceRoots, LintOptions options) throws IOException {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        List<SourceRoot> normalizedSourceRoots = sourceRoots.stream()
                .map(sourceRoot -> new SourceRoot(sourceRoot.path().toAbsolutePath().normalize(), sourceRoot.moduleId()))
                .distinct()
                .toList();
        Path primarySourceDirectory = normalizedSourceRoots.isEmpty() ? normalizedRoot : normalizedSourceRoots.get(0).path();

        if (normalizedSourceRoots.isEmpty()) {
            return new ProjectContext(normalizedRoot, primarySourceDirectory, List.of(), List.of(), AnnotationMetadataIndex.empty(), options);
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
                documentsByPath.values().stream().collect(Collectors.toList()),
                AnnotationMetadataIndex.build(documentsByPath.values().stream().collect(Collectors.toList())),
                options
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

    public AnnotationMetadataIndex annotationMetadataIndex() {
        return annotationMetadataIndex;
    }

    public LintOptions options() {
        return options;
    }

    public SpringSemanticFacts springFacts(SourceUnit sourceUnit) {
        return semanticFactsBySourceUnit.computeIfAbsent(sourceUnit, ignored -> SpringSemanticFacts.create(this));
    }

    public TypeResolutionIndex typeResolutionIndex() {
        TypeResolutionIndex cached = typeResolutionIndex;
        if (cached != null) {
            return cached;
        }
        TypeResolutionIndex built = TypeResolutionIndex.build(this);
        typeResolutionIndex = built;
        return built;
    }
}
