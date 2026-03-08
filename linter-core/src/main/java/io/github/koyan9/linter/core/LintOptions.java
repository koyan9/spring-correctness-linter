/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.nio.file.Path;
import java.util.Map;

public record LintOptions(
        boolean honorInlineSuppressions,
        boolean applyBaseline,
        Path baselineFile,
        Path analysisCacheFile,
        boolean useIncrementalCache,
        Map<String, Path> moduleBaselineFiles,
        Map<String, Path> moduleAnalysisCacheFiles
) {

    public LintOptions(boolean honorInlineSuppressions, boolean applyBaseline, Path baselineFile) {
        this(honorInlineSuppressions, applyBaseline, baselineFile, null, false, Map.of(), Map.of());
    }

    public LintOptions(boolean honorInlineSuppressions, boolean applyBaseline, Path baselineFile, Path analysisCacheFile, boolean useIncrementalCache) {
        this(honorInlineSuppressions, applyBaseline, baselineFile, analysisCacheFile, useIncrementalCache, Map.of(), Map.of());
    }

    public LintOptions {
        moduleBaselineFiles = Map.copyOf(moduleBaselineFiles);
        moduleAnalysisCacheFiles = Map.copyOf(moduleAnalysisCacheFiles);
    }

    public static LintOptions defaults() {
        return new LintOptions(true, true, null, null, false, Map.of(), Map.of());
    }
}
