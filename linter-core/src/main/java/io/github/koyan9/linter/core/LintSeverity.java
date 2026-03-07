/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

public enum LintSeverity {
    INFO,
    WARNING,
    ERROR;

    public boolean isAtLeast(LintSeverity threshold) {
        return this.ordinal() >= threshold.ordinal();
    }
}