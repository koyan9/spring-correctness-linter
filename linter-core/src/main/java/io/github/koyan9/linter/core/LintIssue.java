/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.nio.file.Path;

public record LintIssue(
        String ruleId,
        LintSeverity severity,
        String message,
        Path file,
        int line
) {
}
