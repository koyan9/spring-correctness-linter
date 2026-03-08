/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.LintOptions;
import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.SourceRoot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

record MojoExecutionPlan(
        Path projectRoot,
        Path reportsRoot,
        Path baselinePath,
        List<SourceRoot> sourceRoots,
        Map<String, Path> moduleBaselineFiles,
        List<LintRule> rules,
        LintOptions options
) {
}
