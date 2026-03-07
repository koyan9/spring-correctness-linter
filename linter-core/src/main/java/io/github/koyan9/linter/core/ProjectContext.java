/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProjectContext {

    private final Path projectRoot;
    private final Path sourceDirectory;
    private final List<SourceUnit> sourceUnits;

    private ProjectContext(Path projectRoot, Path sourceDirectory, List<SourceUnit> sourceUnits) {
        this.projectRoot = projectRoot;
        this.sourceDirectory = sourceDirectory;
        this.sourceUnits = List.copyOf(sourceUnits);
    }

    public static ProjectContext load(Path projectRoot, Path sourceDirectory) throws IOException {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedSource = sourceDirectory.toAbsolutePath().normalize();

        if (!Files.exists(normalizedSource)) {
            return new ProjectContext(normalizedRoot, normalizedSource, List.of());
        }

        try (Stream<Path> stream = Files.walk(normalizedSource)) {
            List<SourceUnit> units = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(ProjectContext::readUnit)
                    .collect(Collectors.toList());
            return new ProjectContext(normalizedRoot, normalizedSource, units);
        }
    }

    private static SourceUnit readUnit(Path path) {
        try {
            return new SourceUnit(path.toAbsolutePath().normalize(), Files.readString(path));
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

    public List<SourceUnit> sourceUnits() {
        return sourceUnits;
    }
}
