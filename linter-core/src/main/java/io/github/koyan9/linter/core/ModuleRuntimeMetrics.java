/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

public record ModuleRuntimeMetrics(
        String moduleId,
        long sourceFileCount,
        long analyzedFileCount,
        long cachedFileCount,
        long parseProblemFileCount,
        long analysisMillis,
        long analyzedMillis,
        long cachedMillis
) {

    public long cacheHitRatePercent() {
        if (sourceFileCount == 0) {
            return 0;
        }
        return Math.round((double) cachedFileCount * 100 / sourceFileCount);
    }
}
