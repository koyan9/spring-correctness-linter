/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;
import java.util.Optional;

public record SourceUnit(Path path, String content, Optional<CompilationUnit> compilationUnit) {

    public SourceUnit(Path path, String content) {
        this(path, content, JavaSourceInspector.parseCompilationUnit(content));
    }

    public String relativePath(Path projectRoot) {
        return projectRoot.relativize(path).toString().replace('\\', '/');
    }
}