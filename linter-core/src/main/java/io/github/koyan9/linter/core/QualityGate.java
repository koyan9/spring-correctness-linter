/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

public final class QualityGate {

    private QualityGate() {
    }

    public static boolean shouldFail(LintReport report, LintSeverity minimumSeverity) {
        if (minimumSeverity == null) {
            return false;
        }
        return report.issues().stream().anyMatch(issue -> issue.severity().isAtLeast(minimumSeverity));
    }
}