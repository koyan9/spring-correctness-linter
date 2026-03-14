/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record SourceUnit(Path path, String content, Optional<CompilationUnit> compilationUnit, List<String> parseProblems, SourceStructure structure) {

    public SourceUnit(Path path, String content) {
        this(path, content, JavaSourceInspector.inspect(content));
    }

    SourceUnit(Path path, String content, JavaSourceInspector.ParseOutcome parseOutcome) {
        this(
                path,
                content,
                parseOutcome.compilationUnit(),
                parseOutcome.problems(),
                JavaSourceInspector.buildStructure(parseOutcome.compilationUnit())
        );
    }

    public SourceUnit {
        parseProblems = List.copyOf(parseProblems);
        structure = structure == null ? SourceStructure.empty() : structure;
    }

    public String relativePath(Path projectRoot) {
        return projectRoot.relativize(path).toString().replace('\\', '/');
    }

    public boolean hasParseProblems() {
        return !parseProblems.isEmpty();
    }
}
