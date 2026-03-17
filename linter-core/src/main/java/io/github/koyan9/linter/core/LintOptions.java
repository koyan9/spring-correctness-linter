/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public record LintOptions(
        boolean honorInlineSuppressions,
        boolean applyBaseline,
        Path baselineFile,
        Path analysisCacheFile,
        boolean useIncrementalCache,
        Map<String, Path> moduleBaselineFiles,
        Map<String, Path> moduleAnalysisCacheFiles,
        Set<RuleDomain> enabledRuleDomains,
        Set<RuleDomain> disabledRuleDomains,
        Set<String> enabledRuleIds,
        Set<String> disabledRuleIds,
        boolean assumeCentralizedSecurity,
        boolean autoDetectCentralizedSecurity,
        Set<String> customSecurityAnnotations,
        Set<String> cacheDefaultKeyCacheNames
) {

    public LintOptions(boolean honorInlineSuppressions, boolean applyBaseline, Path baselineFile) {
        this(honorInlineSuppressions, applyBaseline, baselineFile, null, false, Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false, Set.of(), Set.of());
    }

    public LintOptions(boolean honorInlineSuppressions, boolean applyBaseline, Path baselineFile, Path analysisCacheFile, boolean useIncrementalCache) {
        this(honorInlineSuppressions, applyBaseline, baselineFile, analysisCacheFile, useIncrementalCache, Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false, Set.of(), Set.of());
    }

    public LintOptions(
            boolean honorInlineSuppressions,
            boolean applyBaseline,
            Path baselineFile,
            Path analysisCacheFile,
            boolean useIncrementalCache,
            Map<String, Path> moduleBaselineFiles,
            Map<String, Path> moduleAnalysisCacheFiles
    ) {
        this(honorInlineSuppressions, applyBaseline, baselineFile, analysisCacheFile, useIncrementalCache, moduleBaselineFiles, moduleAnalysisCacheFiles, Set.of(), Set.of(), Set.of(), Set.of(), false, false, Set.of(), Set.of());
    }

    public LintOptions {
        moduleBaselineFiles = Map.copyOf(moduleBaselineFiles);
        moduleAnalysisCacheFiles = Map.copyOf(moduleAnalysisCacheFiles);
        enabledRuleDomains = Set.copyOf(enabledRuleDomains);
        disabledRuleDomains = Set.copyOf(disabledRuleDomains);
        enabledRuleIds = Set.copyOf(enabledRuleIds);
        disabledRuleIds = Set.copyOf(disabledRuleIds);
        customSecurityAnnotations = Set.copyOf(customSecurityAnnotations);
        cacheDefaultKeyCacheNames = Set.copyOf(cacheDefaultKeyCacheNames);
    }

    public static LintOptions defaults() {
        return new LintOptions(true, true, null, null, false, Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false, Set.of(), Set.of());
    }

    public LintOptions withAssumeCentralizedSecurity(boolean value) {
        return new LintOptions(
                honorInlineSuppressions,
                applyBaseline,
                baselineFile,
                analysisCacheFile,
                useIncrementalCache,
                moduleBaselineFiles,
                moduleAnalysisCacheFiles,
                enabledRuleDomains,
                disabledRuleDomains,
                enabledRuleIds,
                disabledRuleIds,
                value,
                autoDetectCentralizedSecurity,
                customSecurityAnnotations,
                cacheDefaultKeyCacheNames
        );
    }

    public LintOptions withAutoDetectCentralizedSecurity(boolean value) {
        return new LintOptions(
                honorInlineSuppressions,
                applyBaseline,
                baselineFile,
                analysisCacheFile,
                useIncrementalCache,
                moduleBaselineFiles,
                moduleAnalysisCacheFiles,
                enabledRuleDomains,
                disabledRuleDomains,
                enabledRuleIds,
                disabledRuleIds,
                assumeCentralizedSecurity,
                value,
                customSecurityAnnotations,
                cacheDefaultKeyCacheNames
        );
    }

    public LintOptions withCustomSecurityAnnotations(Set<String> annotations) {
        return new LintOptions(
                honorInlineSuppressions,
                applyBaseline,
                baselineFile,
                analysisCacheFile,
                useIncrementalCache,
                moduleBaselineFiles,
                moduleAnalysisCacheFiles,
                enabledRuleDomains,
                disabledRuleDomains,
                enabledRuleIds,
                disabledRuleIds,
                assumeCentralizedSecurity,
                autoDetectCentralizedSecurity,
                annotations,
                cacheDefaultKeyCacheNames
        );
    }

    public LintOptions withCacheDefaultKeyCacheNames(Set<String> cacheNames) {
        return new LintOptions(
                honorInlineSuppressions,
                applyBaseline,
                baselineFile,
                analysisCacheFile,
                useIncrementalCache,
                moduleBaselineFiles,
                moduleAnalysisCacheFiles,
                enabledRuleDomains,
                disabledRuleDomains,
                enabledRuleIds,
                disabledRuleIds,
                assumeCentralizedSecurity,
                autoDetectCentralizedSecurity,
                customSecurityAnnotations,
                cacheNames
        );
    }
}
