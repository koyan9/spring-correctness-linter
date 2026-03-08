/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.LintReport;
import io.github.koyan9.linter.core.LintSeverity;
import io.github.koyan9.linter.core.QualityGate;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

final class MojoFailureMessageBuilder {

    String qualityGateFailure(LintReport report, LintSeverity threshold, Path reportsRoot) {
        Set<String> failingModules = QualityGate.failingModules(report, threshold);
        String moduleMessage = formatModules(failingModules);
        return "Lint quality gate failed at severity " + threshold + "." + moduleMessage + " See reports in " + reportsRoot;
    }

    String visibleIssuesFailure(LintReport report, Path reportsRoot) {
        Set<String> failingModules = report.issues().stream()
                .map(issue -> report.moduleFor(issue.file()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String moduleMessage = formatModules(failingModules);
        return "Lint issues detected." + moduleMessage + " See reports in " + reportsRoot;
    }

    private String formatModules(Set<String> failingModules) {
        return failingModules.isEmpty()
                ? ""
                : " Failing modules: " + failingModules.stream().collect(Collectors.joining(", ")) + ".";
    }
}
