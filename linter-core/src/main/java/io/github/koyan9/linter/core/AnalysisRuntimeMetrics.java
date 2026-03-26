/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.Comparator;
import java.util.List;

public record AnalysisRuntimeMetrics(
        boolean incrementalCacheEnabled,
        String cacheScope,
        String analysisFingerprint,
        long totalElapsedMillis,
        long sourceFileCount,
        long analyzedFileCount,
        long cachedFileCount,
        long parseProblemFileCount,
        List<String> cacheMissReasons,
        AnalysisPhaseMetrics phaseMetrics,
        List<ModuleRuntimeMetrics> moduleMetrics
) {

    public AnalysisRuntimeMetrics {
        cacheScope = cacheScope == null ? "disabled" : cacheScope;
        analysisFingerprint = analysisFingerprint == null ? "" : analysisFingerprint;
        cacheMissReasons = cacheMissReasons == null ? List.of() : List.copyOf(cacheMissReasons);
        phaseMetrics = phaseMetrics == null ? AnalysisPhaseMetrics.empty() : phaseMetrics;
        moduleMetrics = moduleMetrics == null
                ? List.of()
                : moduleMetrics.stream()
                        .sorted(Comparator.comparing(ModuleRuntimeMetrics::moduleId))
                        .toList();
    }

    public long cacheHitRatePercent() {
        if (sourceFileCount == 0) {
            return 0;
        }
        return Math.round((double) cachedFileCount * 100 / sourceFileCount);
    }

    public ModuleRuntimeMetrics moduleMetric(String moduleId) {
        return moduleMetrics.stream()
                .filter(metric -> metric.moduleId().equals(moduleId))
                .findFirst()
                .orElse(null);
    }

    public List<ModuleRuntimeMetrics> slowestModules(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return moduleMetrics.stream()
                .filter(metric -> metric.analyzedMillis() > 0)
                .sorted(Comparator.comparingLong(ModuleRuntimeMetrics::analyzedMillis).reversed())
                .limit(limit)
                .toList();
    }

    public static AnalysisRuntimeMetrics empty() {
        return new AnalysisRuntimeMetrics(false, "disabled", "", 0, 0, 0, 0, 0, List.of(), AnalysisPhaseMetrics.empty(), List.of());
    }
}
