/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

public record AnalysisPhaseMetrics(
        long contextLoadMillis,
        long cacheLoadMillis,
        long fileAnalysisMillis,
        long cacheWriteMillis,
        long baselineLoadMillis,
        long baselineFilterMillis,
        long reportAssemblyMillis
) {

    public long totalTrackedMillis() {
        return contextLoadMillis
                + cacheLoadMillis
                + fileAnalysisMillis
                + cacheWriteMillis
                + baselineLoadMillis
                + baselineFilterMillis
                + reportAssemblyMillis;
    }

    public static AnalysisPhaseMetrics empty() {
        return new AnalysisPhaseMetrics(0, 0, 0, 0, 0, 0, 0);
    }
}
