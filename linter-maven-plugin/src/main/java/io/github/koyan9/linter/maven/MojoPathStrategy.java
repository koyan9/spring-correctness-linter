/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.SourceRoot;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MojoPathStrategy {

    Map<String, Path> resolveModuleScopedPaths(List<SourceRoot> sourceRoots, Path basePath) {
        if (basePath == null) {
            return Map.of();
        }
        LinkedHashMap<String, Path> scopedPaths = new LinkedHashMap<>();
        for (SourceRoot sourceRoot : sourceRoots) {
            scopedPaths.putIfAbsent(sourceRoot.moduleId(), moduleScopedPath(basePath, sourceRoot.moduleId()));
        }
        return scopedPaths;
    }

    private Path moduleScopedPath(Path basePath, String moduleId) {
        Path normalizedBasePath = basePath.toAbsolutePath().normalize();
        Path parent = normalizedBasePath.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }
        String sanitizedModuleId = sanitizeModuleId(moduleId);
        return parent.resolve("modules").resolve(sanitizedModuleId).resolve(normalizedBasePath.getFileName().toString());
    }

    private String sanitizeModuleId(String moduleId) {
        if (moduleId == null || moduleId.isBlank() || ".".equals(moduleId)) {
            return "root";
        }
        return moduleId.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
