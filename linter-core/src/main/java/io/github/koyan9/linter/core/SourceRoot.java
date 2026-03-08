/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.nio.file.Path;

public record SourceRoot(Path path, String moduleId) {

    public SourceRoot {
        moduleId = moduleId == null || moduleId.isBlank() ? "." : moduleId;
    }

    public static SourceRoot of(Path projectRoot, Path path) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        String moduleId;
        try {
            Path relative = normalizedRoot.relativize(normalizedPath);
            moduleId = relative.getNameCount() == 0 ? "." : relative.toString().replace('\\', '/');
        } catch (IllegalArgumentException exception) {
            moduleId = normalizedPath.toString().replace('\\', '/');
        }
        return new SourceRoot(normalizedPath, moduleId);
    }
}
