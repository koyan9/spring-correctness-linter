/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProjectContext {

    private final Path projectRoot;
    private final Path sourceDirectory;
    private final List<SourceRoot> sourceRoots;
    private final List<SourceDocument> sourceDocuments;
    private final Map<Path, JavaSourceInspector.ParseOutcome> parseOutcomes;
    private final AnnotationMetadataIndex annotationMetadataIndex;
    private final LintOptions options;
    private final Map<Path, SourceUnit> sourceUnitsByPath = new ConcurrentHashMap<>();
    private final Map<Path, SpringSemanticFacts> semanticFactsBySourcePath = new ConcurrentHashMap<>();
    private volatile TypeResolutionIndex typeResolutionIndex;
    private volatile Boolean securityFilterChainBeanPresent;
    private volatile Boolean keyGeneratorBeanPresent;

    private ProjectContext(
            Path projectRoot,
            Path sourceDirectory,
            List<SourceRoot> sourceRoots,
            List<SourceDocument> sourceDocuments,
            Map<Path, JavaSourceInspector.ParseOutcome> parseOutcomes,
            AnnotationMetadataIndex annotationMetadataIndex,
            LintOptions options
    ) {
        this.projectRoot = projectRoot;
        this.sourceDirectory = sourceDirectory;
        this.sourceRoots = List.copyOf(sourceRoots);
        this.sourceDocuments = List.copyOf(sourceDocuments);
        this.parseOutcomes = Map.copyOf(parseOutcomes);
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
            return new ProjectContext(normalizedRoot, primarySourceDirectory, List.of(), List.of(), Map.of(), AnnotationMetadataIndex.empty(), options);
        }

        Map<Path, SourceDocument> documentsByPath = new LinkedHashMap<>();
        Map<Path, JavaSourceInspector.ParseOutcome> parseOutcomes = new LinkedHashMap<>();
        for (SourceRoot sourceRoot : normalizedSourceRoots) {
            Path normalizedSource = sourceRoot.path();
            if (!Files.exists(normalizedSource)) {
                continue;
            }

            try (Stream<Path> stream = Files.walk(normalizedSource)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(path -> readDocument(path, sourceRoot.moduleId(), parseOutcomes))
                        .forEach(document -> documentsByPath.put(document.path(), document));
            }
        }

        return new ProjectContext(
                normalizedRoot,
                primarySourceDirectory,
                normalizedSourceRoots,
                documentsByPath.values().stream().collect(Collectors.toList()),
                parseOutcomes,
                AnnotationMetadataIndex.build(documentsByPath.values().stream().collect(Collectors.toList()), parseOutcomes),
                options
        );
    }

    private static SourceDocument readDocument(Path path, String moduleId, Map<Path, JavaSourceInspector.ParseOutcome> parseOutcomes) {
        try {
            Path normalizedPath = path.toAbsolutePath().normalize();
            byte[] bytes = Files.readAllBytes(normalizedPath);
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (content.contains("@interface")) {
                parseOutcomes.put(normalizedPath, JavaSourceInspector.inspect(content));
            }
            return new SourceDocument(normalizedPath, content, SourceDocument.sha256(bytes), moduleId);
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
        return sourceDocuments.stream()
                .map(this::sourceUnitFor)
                .toList();
    }

    public AnnotationMetadataIndex annotationMetadataIndex() {
        return annotationMetadataIndex;
    }

    public LintOptions options() {
        return options;
    }

    JavaSourceInspector.ParseOutcome parseOutcomeFor(SourceDocument sourceDocument) {
        return parseOutcomes.get(sourceDocument.path());
    }

    SourceUnit sourceUnitFor(SourceDocument sourceDocument) {
        Path key = sourceDocument.path().toAbsolutePath().normalize();
        return sourceUnitsByPath.computeIfAbsent(
                key,
                ignored -> sourceDocument.toSourceUnit(parseOutcomeFor(sourceDocument))
        );
    }

    public SpringSemanticFacts springFacts(SourceUnit sourceUnit) {
        Path key = sourceUnit.path().toAbsolutePath().normalize();
        return semanticFactsBySourcePath.computeIfAbsent(key, ignored -> SpringSemanticFacts.create(this));
    }

    public TypeResolutionIndex typeResolutionIndex() {
        TypeResolutionIndex cached = typeResolutionIndex;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (typeResolutionIndex != null) {
                return typeResolutionIndex;
            }
            TypeResolutionIndex built = TypeResolutionIndex.build(this);
            typeResolutionIndex = built;
            return built;
        }
    }

    public boolean hasSecurityFilterChainBean() {
        Boolean cached = securityFilterChainBeanPresent;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (securityFilterChainBeanPresent != null) {
                return securityFilterChainBeanPresent;
            }
            boolean found = false;
            for (SourceDocument sourceDocument : sourceDocuments) {
                SourceUnit sourceUnit = sourceUnitFor(sourceDocument);
                SpringSemanticFacts facts = springFacts(sourceUnit);
                for (com.github.javaparser.ast.body.MethodDeclaration method : sourceUnit.structure().methods()) {
                    if (!facts.hasAnnotation(method, "Bean")) {
                        continue;
                    }
                    if (isSecurityFilterChainType(method.getType().toString())) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
            securityFilterChainBeanPresent = found;
            return found;
        }
    }

    public boolean hasProjectWideKeyGeneratorBean() {
        Boolean cached = keyGeneratorBeanPresent;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (keyGeneratorBeanPresent != null) {
                return keyGeneratorBeanPresent;
            }
            boolean found = false;
            for (SourceDocument sourceDocument : sourceDocuments) {
                SourceUnit sourceUnit = sourceUnitFor(sourceDocument);
                SpringSemanticFacts facts = springFacts(sourceUnit);
                for (com.github.javaparser.ast.body.MethodDeclaration method : sourceUnit.structure().methods()) {
                    if (!facts.hasAnnotation(method, "Bean")) {
                        continue;
                    }
                    if (isKeyGeneratorType(method.getType().toString())) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
            keyGeneratorBeanPresent = found;
            return found;
        }
    }

    private boolean isSecurityFilterChainType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return false;
        }
        String stripped = rawType.trim();
        int genericStart = stripped.indexOf('<');
        if (genericStart >= 0) {
            stripped = stripped.substring(0, genericStart);
        }
        int lastDot = stripped.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? stripped.substring(lastDot + 1) : stripped;
        return "SecurityFilterChain".equals(simpleName) || "SecurityWebFilterChain".equals(simpleName);
    }

    private boolean isKeyGeneratorType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return false;
        }
        String stripped = rawType.trim();
        int genericStart = stripped.indexOf('<');
        if (genericStart >= 0) {
            stripped = stripped.substring(0, genericStart);
        }
        int lastDot = stripped.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? stripped.substring(lastDot + 1) : stripped;
        return "KeyGenerator".equals(simpleName);
    }
}
