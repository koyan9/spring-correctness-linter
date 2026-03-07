/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.nio.file.Path;

public record BaselineEntry(
        String ruleId,
        String relativePath,
        int line,
        String message
) {

    public static BaselineEntry from(LintIssue issue, Path projectRoot) {
        String path = projectRoot.toAbsolutePath().normalize()
                .relativize(issue.file().toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
        return new BaselineEntry(issue.ruleId(), path, issue.line(), issue.message());
    }
}