/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.LinkedHashSet;
import java.util.Set;

public final class QualityGate {

    private QualityGate() {
    }

    public static boolean shouldFail(LintReport report, LintSeverity minimumSeverity) {
        if (minimumSeverity == null) {
            return false;
        }
        return report.issues().stream().anyMatch(issue -> issue.severity().isAtLeast(minimumSeverity));
    }

    public static Set<String> failingModules(LintReport report, LintSeverity minimumSeverity) {
        Set<String> modules = new LinkedHashSet<>();
        if (minimumSeverity == null) {
            return modules;
        }
        report.issues().stream()
                .filter(issue -> issue.severity().isAtLeast(minimumSeverity))
                .map(issue -> report.moduleFor(issue.file()))
                .forEach(modules::add);
        return modules;
    }
}
