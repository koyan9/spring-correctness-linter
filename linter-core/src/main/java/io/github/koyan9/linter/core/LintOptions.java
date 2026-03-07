/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.nio.file.Path;

public record LintOptions(
        boolean honorInlineSuppressions,
        boolean applyBaseline,
        Path baselineFile
) {

    public static LintOptions defaults() {
        return new LintOptions(true, true, null);
    }
}