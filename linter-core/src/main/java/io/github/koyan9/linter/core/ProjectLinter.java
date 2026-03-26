/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ProjectLinter {

    private static final Set<String> TYPE_RESOLUTION_RULE_IDS = Set.of(
            "SPRING_ASYNC_SELF_INVOCATION",
            "SPRING_TX_SELF_INVOCATION",
            "SPRING_ENDPOINT_SECURITY"
    );

    private final List<LintRule> rules;

    public ProjectLinter(List<LintRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public LintReport analyze(Path projectRoot, Path sourceDirectory) throws IOException {
        return analyze(projectRoot, sourceDirectory, LintOptions.defaults()).report();
    }

    public LintReport analyze(Path projectRoot, List<Path> sourceDirectories) throws IOException {
        return analyze(projectRoot, sourceDirectories, LintOptions.defaults()).report();
    }

    public LintAnalysisResult analyzeSourceRoots(Path projectRoot, List<SourceRoot> sourceRoots, LintOptions options) throws IOException {
        return analyzeInternal(projectRoot, sourceRoots, options);
    }

    public LintAnalysisResult analyze(Path projectRoot, Path sourceDirectory, LintOptions options) throws IOException {
        return analyze(projectRoot, List.of(sourceDirectory), options);
    }

    public LintAnalysisResult analyze(Path projectRoot, List<Path> sourceDirectories, LintOptions options) throws IOException {
        return analyzeInternal(projectRoot, sourceDirectories.stream().map(path -> SourceRoot.of(projectRoot, path)).toList(), options);
    }

    private LintAnalysisResult analyzeInternal(Path projectRoot, List<SourceRoot> sourceRoots, LintOptions options) throws IOException {
        long totalStartNanos = System.nanoTime();

        long contextLoadStartNanos = System.nanoTime();
        ProjectContext context = ProjectContext.loadSourceRoots(projectRoot, sourceRoots, options);
        long contextLoadNanos = System.nanoTime() - contextLoadStartNanos;

        List<LintIssue> baselineCandidates = new ArrayList<>();
        List<SourceParseProblem> parseProblems = new ArrayList<>();
        long suppressedIssueCount = 0;
        long cachedFileCount = 0;
        List<CachedFileAnalysis> cacheEntries = new ArrayList<>();

        long cacheLoadStartNanos = System.nanoTime();
        LoadedCachedAnalyses loadedCachedAnalyses = loadCachedAnalyses(context, options);
        long cacheLoadNanos = System.nanoTime() - cacheLoadStartNanos;
        Map<String, CachedFileAnalysis> cachedAnalyses = loadedCachedAnalyses.analyses();

        Map<String, String> fileModules = new HashMap<>();
        Map<String, ModuleAccumulator> modules = initializeModules(context);

        if (requiresTypeResolution()) {
            context.typeResolutionIndex();
        }

        long fileAnalysisStartNanos = System.nanoTime();
        List<FileAnalysisResult> fileResults = analyzeDocuments(context, options, cachedAnalyses);
        long fileAnalysisNanos = System.nanoTime() - fileAnalysisStartNanos;

        for (FileAnalysisResult result : fileResults) {
            fileModules.put(result.absolutePathKey(), result.moduleId());
            ModuleAccumulator module = modules.computeIfAbsent(result.moduleId(), ModuleAccumulator::new);
            module.sourceFileCount += result.sourceFileCount();
            module.analyzedFileCount += result.analyzedFileCount();
            module.cachedFileCount += result.cachedFileCount();
            module.parseProblemFileCount += result.parseProblemFileCount();
            module.analysisNanos += result.analysisNanos();
            module.analyzedNanos += result.analyzedNanos();
            module.cachedNanos += result.cachedNanos();

            cachedFileCount += result.cachedFileCount();
            suppressedIssueCount += result.suppressedIssueCount();
            baselineCandidates.addAll(result.baselineCandidates());
            parseProblems.addAll(result.parseProblems());
            cacheEntries.add(result.cacheEntry());
        }

        long cacheWriteStartNanos = System.nanoTime();
        writeCachedAnalyses(options, loadedCachedAnalyses.analysisFingerprint(), cacheEntries);
        long cacheWriteNanos = System.nanoTime() - cacheWriteStartNanos;

        long baselineLoadStartNanos = System.nanoTime();
        LoadedBaselineEntries loadedBaselineEntries = loadBaselineEntries(options);
        long baselineLoadNanos = System.nanoTime() - baselineLoadStartNanos;
        Set<BaselineEntry> baselineEntries = loadedBaselineEntries.entries();

        Set<BaselineEntry> matchedEntries = new HashSet<>();
        List<LintIssue> visibleIssues = new ArrayList<>();
        long baselineMatchedIssueCount = 0;
        Map<String, BaselineDiffAccumulator> baselineModules = initializeBaselineDiffModules(modules.keySet());

        long baselineFilterStartNanos = System.nanoTime();
        for (LintIssue issue : baselineCandidates) {
            BaselineEntry entry = BaselineEntry.from(issue, context.projectRoot());
            String moduleId = fileModules.getOrDefault(issue.file().toAbsolutePath().normalize().toString(), ".");
            if (baselineEntries.contains(entry)) {
                baselineMatchedIssueCount++;
                matchedEntries.add(entry);
                baselineModules.computeIfAbsent(moduleId, BaselineDiffAccumulator::new).matchedBaselineCount++;
                continue;
            }
            visibleIssues.add(issue);
            baselineModules.computeIfAbsent(moduleId, BaselineDiffAccumulator::new).newIssueCount++;
        }

        Set<BaselineEntry> staleEntries = new HashSet<>(baselineEntries);
        staleEntries.removeAll(matchedEntries);
        Map<String, String> baselineEntryModules = new HashMap<>();
        for (BaselineEntry staleEntry : staleEntries) {
            String moduleId = moduleIdForBaselineEntry(staleEntry, loadedBaselineEntries.moduleScopeByEntry(), context);
            baselineEntryModules.put(staleEntry.relativePath(), moduleId);
            baselineModules.computeIfAbsent(moduleId, BaselineDiffAccumulator::new).staleBaselineCount++;
        }
        for (BaselineEntry matchedEntry : matchedEntries) {
            String moduleId = moduleIdForBaselineEntry(matchedEntry, loadedBaselineEntries.moduleScopeByEntry(), context);
            baselineEntryModules.putIfAbsent(matchedEntry.relativePath(), moduleId);
        }

        List<RuleDescriptor> descriptors = rules.stream()
                .map(rule -> new RuleDescriptor(
                        rule.id(),
                        rule.title(),
                        rule.description(),
                        rule.domain(),
                        rule.severity(),
                        rule.appliesWhen(),
                        rule.commonFalsePositiveBoundaries(),
                        rule.recommendedFixes()
                ))
                .toList();
        RuleDomainSelectionSummary ruleDomainSelection = RuleDomainSelectionSummary.fromConfiguredAndRules(
                options.enabledRuleDomains(),
                options.disabledRuleDomains(),
                options.enabledRuleIds(),
                options.disabledRuleIds(),
                descriptors
        );
        for (LintIssue issue : visibleIssues) {
            modules.computeIfAbsent(fileModules.getOrDefault(issue.file().toAbsolutePath().normalize().toString(), "."), ModuleAccumulator::new).visibleIssueCount++;
        }
        long baselineFilterNanos = System.nanoTime() - baselineFilterStartNanos;

        long reportAssemblyStartNanos = System.nanoTime();
        BaselineDiffReport baselineDiffReport = new BaselineDiffReport(
                visibleIssues,
                matchedEntries,
                staleEntries,
                baselineModules.values().stream().map(BaselineDiffAccumulator::toSummary).toList(),
                fileModules,
                baselineEntryModules,
                descriptors,
                ruleDomainSelection
        );
        List<ModuleRuntimeMetrics> moduleRuntimeMetrics = modules.values().stream()
                .map(ModuleAccumulator::toRuntimeMetrics)
                .toList();
        long reportAssemblyNanos = System.nanoTime() - reportAssemblyStartNanos;
        List<String> cacheMissReasons = determineCacheMissReasons(options, loadedCachedAnalyses, cachedFileCount);

        AnalysisRuntimeMetrics runtimeMetrics = new AnalysisRuntimeMetrics(
                options.useIncrementalCache(),
                cacheScope(options),
                loadedCachedAnalyses.analysisFingerprint().value(),
                toMillis(System.nanoTime() - totalStartNanos),
                context.sourceDocuments().size(),
                context.sourceDocuments().size() - cachedFileCount,
                cachedFileCount,
                parseProblems.size(),
                cacheMissReasons,
                new AnalysisPhaseMetrics(
                        toMillis(contextLoadNanos),
                        toMillis(cacheLoadNanos),
                        toMillis(fileAnalysisNanos),
                        toMillis(cacheWriteNanos),
                        toMillis(baselineLoadNanos),
                        toMillis(baselineFilterNanos),
                        toMillis(reportAssemblyNanos)
                ),
                moduleRuntimeMetrics
        );

        LintReport report = new LintReport(
                context.projectRoot(),
                context.sourceDirectory(),
                context.sourceDirectories(),
                Instant.now(),
                descriptors,
                visibleIssues,
                suppressedIssueCount,
                baselineMatchedIssueCount,
                staleEntries.size(),
                cachedFileCount,
                modules.values().stream().map(ModuleAccumulator::toSummary).toList(),
                fileModules,
                parseProblems,
                runtimeMetrics,
                ruleDomainSelection
        );
        return new LintAnalysisResult(
                report,
                baselineCandidates,
                baselineDiffReport
        );
    }

    private LoadedBaselineEntries loadBaselineEntries(LintOptions options) throws IOException {
        if (!options.applyBaseline()) {
            return LoadedBaselineEntries.empty();
        }

        BaselineStore baselineStore = new BaselineStore();
        Set<BaselineEntry> baselineEntries = new HashSet<>();
        Map<BaselineEntry, String> moduleScopeByEntry = new HashMap<>();
        if (options.baselineFile() != null) {
            baselineEntries.addAll(baselineStore.load(options.baselineFile()));
        }
        for (Map.Entry<String, Path> entry : options.moduleBaselineFiles().entrySet()) {
            Set<BaselineEntry> moduleEntries = baselineStore.load(entry.getValue());
            baselineEntries.addAll(moduleEntries);
            moduleEntries.forEach(baselineEntry -> moduleScopeByEntry.putIfAbsent(baselineEntry, entry.getKey()));
        }
        return new LoadedBaselineEntries(baselineEntries, moduleScopeByEntry);
    }

    private LoadedCachedAnalyses loadCachedAnalyses(ProjectContext context, LintOptions options) throws IOException {
        AnalysisCacheStore.CacheFingerprint analysisFingerprint = cacheFingerprint(context, options);
        if (!options.useIncrementalCache() || options.analysisCacheFile() == null) {
            if (!options.useIncrementalCache() || options.moduleAnalysisCacheFiles().isEmpty()) {
                List<String> reasons = options.useIncrementalCache()
                        ? List.of(AnalysisCacheStore.CACHE_REASON_CACHE_NOT_CONFIGURED)
                        : List.of();
                return new LoadedCachedAnalyses(Map.of(), analysisFingerprint, reasons);
            }
        }
        AnalysisCacheStore cacheStore = new AnalysisCacheStore();
        Map<String, CachedFileAnalysis> cachedAnalyses = new HashMap<>();
        LinkedHashSet<String> cacheMissReasons = new LinkedHashSet<>();
        if (options.analysisCacheFile() != null) {
            AnalysisCacheStore.CacheState cacheState = cacheStore.load(options.analysisCacheFile(), analysisFingerprint);
            cachedAnalyses.putAll(cacheState.analyses());
            cacheMissReasons.addAll(cacheState.missReasons());
        }
        for (Path moduleCacheFile : options.moduleAnalysisCacheFiles().values()) {
            AnalysisCacheStore.CacheState cacheState = cacheStore.load(moduleCacheFile, analysisFingerprint);
            cachedAnalyses.putAll(cacheState.analyses());
            cacheMissReasons.addAll(cacheState.missReasons());
        }
        return new LoadedCachedAnalyses(cachedAnalyses, analysisFingerprint, List.copyOf(cacheMissReasons));
    }

    private void writeCachedAnalyses(
            LintOptions options,
            AnalysisCacheStore.CacheFingerprint analysisFingerprint,
            List<CachedFileAnalysis> analyses
    ) throws IOException {
        if (!options.useIncrementalCache()) {
            return;
        }
        AnalysisCacheStore cacheStore = new AnalysisCacheStore();
        if (!options.moduleAnalysisCacheFiles().isEmpty()) {
            for (Map.Entry<String, Path> entry : options.moduleAnalysisCacheFiles().entrySet()) {
                List<CachedFileAnalysis> moduleAnalyses = analyses.stream()
                        .filter(analysis -> analysis.moduleId().equals(entry.getKey()))
                        .toList();
                cacheStore.write(entry.getValue(), analysisFingerprint, moduleAnalyses);
            }
            return;
        }
        if (options.analysisCacheFile() != null) {
            cacheStore.write(options.analysisCacheFile(), analysisFingerprint, analyses);
        }
    }

    private AnalysisCacheStore.CacheFingerprint cacheFingerprint(ProjectContext context, LintOptions options) {
        if (!options.useIncrementalCache()) {
            return new AnalysisCacheStore.CacheFingerprint("", Map.of());
        }
        AnalysisCacheStore cacheStore = new AnalysisCacheStore();
        boolean includeAutoDetectContext = options.autoDetectCentralizedSecurity() || options.autoDetectProjectWideKeyGenerator();
        return cacheStore.fingerprint(rules, options, context, requiresTypeResolution(), includeAutoDetectContext);
    }

    private List<String> determineCacheMissReasons(
            LintOptions options,
            LoadedCachedAnalyses loadedCachedAnalyses,
            long cachedFileCount
    ) {
        if (!options.useIncrementalCache() || cachedFileCount > 0) {
            return List.of();
        }
        if (!loadedCachedAnalyses.missReasons().isEmpty()) {
            return loadedCachedAnalyses.missReasons();
        }
        if (!loadedCachedAnalyses.analyses().isEmpty()) {
            return List.of(AnalysisCacheStore.CACHE_REASON_MODIFIED_OR_NEW_FILES);
        }
        return List.of();
    }

    private String cacheScope(LintOptions options) {
        if (!options.useIncrementalCache()) {
            return "disabled";
        }
        if (!options.moduleAnalysisCacheFiles().isEmpty()) {
            return "per-module";
        }
        if (options.analysisCacheFile() != null) {
            return "shared-file";
        }
        return "disabled";
    }

    private long toMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private Map<String, ModuleAccumulator> initializeModules(ProjectContext context) {
        Map<String, ModuleAccumulator> modules = new HashMap<>();
        for (SourceRoot sourceRoot : context.sourceRoots()) {
            modules.computeIfAbsent(sourceRoot.moduleId(), ModuleAccumulator::new).sourceDirectoryCount++;
        }
        return modules;
    }

    private Map<String, BaselineDiffAccumulator> initializeBaselineDiffModules(Set<String> moduleIds) {
        Map<String, BaselineDiffAccumulator> modules = new HashMap<>();
        for (String moduleId : moduleIds) {
            modules.put(moduleId, new BaselineDiffAccumulator(moduleId));
        }
        return modules;
    }

    private List<FileAnalysisResult> analyzeDocuments(
            ProjectContext context,
            LintOptions options,
            Map<String, CachedFileAnalysis> cachedAnalyses
    ) {
        List<SourceDocument> documents = context.sourceDocuments();
        if (documents.size() <= 1 || !options.parallelFileAnalysis()) {
            return documents.stream()
                    .map(document -> analyzeDocument(document, context, options, cachedAnalyses))
                    .toList();
        }
        int parallelism = resolveParallelism(documents.size(), options.fileAnalysisParallelism());
        if (parallelism <= 1) {
            return documents.stream()
                    .map(document -> analyzeDocument(document, context, options, cachedAnalyses))
                    .toList();
        }

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<FileAnalysisResult>> futures = new ArrayList<>(documents.size());
            for (SourceDocument document : documents) {
                futures.add(executor.submit(() -> analyzeDocument(document, context, options, cachedAnalyses)));
            }
            List<FileAnalysisResult> results = new ArrayList<>(documents.size());
            for (Future<FileAnalysisResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while analyzing source files", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Failed to analyze source files", exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private FileAnalysisResult analyzeDocument(
            SourceDocument sourceDocument,
            ProjectContext context,
            LintOptions options,
            Map<String, CachedFileAnalysis> cachedAnalyses
    ) {
        String relativePath = sourceDocument.relativePath(context.projectRoot());
        String moduleId = sourceDocument.moduleId();
        String absolutePathKey = sourceDocument.path().toAbsolutePath().normalize().toString();

        long fileAnalysisStartedAt = System.nanoTime();
        CachedFileAnalysis cachedAnalysis = cachedAnalyses.get(relativePath);
        if (cachedAnalysis != null && cachedAnalysis.contentHash().equals(sourceDocument.contentHash())) {
            List<LintIssue> baselineCandidates = cachedAnalysis.restoreIssues(context.projectRoot());
            List<SourceParseProblem> parseProblems = cachedAnalysis.parseProblemMessages().isEmpty()
                    ? List.of()
                    : List.of(cachedAnalysis.toParseProblem(context.projectRoot()));
            long elapsed = System.nanoTime() - fileAnalysisStartedAt;
            return new FileAnalysisResult(
                    moduleId,
                    relativePath,
                    absolutePathKey,
                    baselineCandidates,
                    parseProblems,
                    cachedAnalysis.suppressedIssueCount(),
                    1,
                    0,
                    parseProblems.isEmpty() ? 0 : 1,
                    elapsed,
                    0,
                    elapsed,
                    cachedAnalysis
            );
        }

        SourceUnit sourceUnit = context.sourceUnitFor(sourceDocument);
        List<SourceParseProblem> parseProblems = sourceUnit.hasParseProblems()
                ? List.of(new SourceParseProblem(sourceUnit.path(), sourceUnit.parseProblems()))
                : List.of();

        InlineSuppressions inlineSuppressions = options.honorInlineSuppressions()
                ? InlineSuppressions.parse(sourceUnit)
                : InlineSuppressions.none();
        List<LintIssue> fileBaselineCandidates = new ArrayList<>();
        long fileSuppressedIssueCount = 0;
        for (LintRule rule : rules) {
            for (LintIssue issue : rule.evaluate(sourceUnit, context)) {
                if (inlineSuppressions.suppresses(issue)) {
                    fileSuppressedIssueCount++;
                    continue;
                }
                fileBaselineCandidates.add(issue);
            }
        }

        CachedFileAnalysis cacheEntry = CachedFileAnalysis.from(
                context.projectRoot(),
                sourceDocument,
                fileBaselineCandidates,
                fileSuppressedIssueCount,
                sourceUnit.parseProblems()
        );
        long elapsed = System.nanoTime() - fileAnalysisStartedAt;
        return new FileAnalysisResult(
                moduleId,
                relativePath,
                absolutePathKey,
                fileBaselineCandidates,
                parseProblems,
                fileSuppressedIssueCount,
                0,
                1,
                parseProblems.isEmpty() ? 0 : 1,
                elapsed,
                elapsed,
                0,
                cacheEntry
        );
    }

    private String moduleIdForBaselineEntry(
            BaselineEntry entry,
            Map<BaselineEntry, String> scopedModuleIds,
            ProjectContext context
    ) {
        String scopedModuleId = scopedModuleIds.get(entry);
        if (scopedModuleId != null && !scopedModuleId.isBlank()) {
            return scopedModuleId;
        }
        String resolved = moduleIdForRelativePath(entry.relativePath(), context.projectRoot(), context.sourceRoots());
        return resolved == null || resolved.isBlank() ? "." : resolved;
    }

    private String moduleIdForRelativePath(String relativePath, Path projectRoot, List<SourceRoot> sourceRoots) {
        Path resolvedPath = projectRoot.resolve(relativePath).toAbsolutePath().normalize();
        SourceRoot bestMatch = null;
        for (SourceRoot sourceRoot : sourceRoots) {
            if (!resolvedPath.startsWith(sourceRoot.path())) {
                continue;
            }
            if (bestMatch == null || sourceRoot.path().getNameCount() > bestMatch.path().getNameCount()) {
                bestMatch = sourceRoot;
            }
        }
        if (bestMatch != null) {
            return bestMatch.moduleId();
        }
        String normalizedRelativePath = relativePath.replace('\\', '/');
        int slashIndex = normalizedRelativePath.indexOf('/');
        return slashIndex > 0 ? normalizedRelativePath.substring(0, slashIndex) : ".";
    }

    private boolean requiresTypeResolution() {
        return rules.stream().anyMatch(rule -> TYPE_RESOLUTION_RULE_IDS.contains(rule.id()));
    }

    private int resolveParallelism(int documentCount, int configuredParallelism) {
        int resolved = configuredParallelism > 0
                ? configuredParallelism
                : Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(documentCount, resolved));
    }

    private static final class ModuleAccumulator {

        private final String moduleId;
        private long sourceDirectoryCount;
        private long sourceFileCount;
        private long analyzedFileCount;
        private long visibleIssueCount;
        private long parseProblemFileCount;
        private long cachedFileCount;
        private long analysisNanos;
        private long analyzedNanos;
        private long cachedNanos;

        private ModuleAccumulator(String moduleId) {
            this.moduleId = moduleId;
        }

        private ModuleSummary toSummary() {
            return new ModuleSummary(moduleId, sourceDirectoryCount, sourceFileCount, visibleIssueCount, parseProblemFileCount, cachedFileCount);
        }

        private ModuleRuntimeMetrics toRuntimeMetrics() {
            long analysisMillis = TimeUnit.NANOSECONDS.toMillis(analysisNanos);
            long analyzedMillis = TimeUnit.NANOSECONDS.toMillis(analyzedNanos);
            long cachedMillis = TimeUnit.NANOSECONDS.toMillis(cachedNanos);
            return new ModuleRuntimeMetrics(moduleId, sourceFileCount, analyzedFileCount, cachedFileCount, parseProblemFileCount, analysisMillis, analyzedMillis, cachedMillis);
        }
    }

    private static final class BaselineDiffAccumulator {

        private final String moduleId;
        private long newIssueCount;
        private long matchedBaselineCount;
        private long staleBaselineCount;

        private BaselineDiffAccumulator(String moduleId) {
            this.moduleId = moduleId;
        }

        private BaselineDiffModuleSummary toSummary() {
            return new BaselineDiffModuleSummary(moduleId, newIssueCount, matchedBaselineCount, staleBaselineCount);
        }
    }

    private record LoadedCachedAnalyses(
            Map<String, CachedFileAnalysis> analyses,
            AnalysisCacheStore.CacheFingerprint analysisFingerprint,
            List<String> missReasons
    ) {

        private LoadedCachedAnalyses {
            analyses = Map.copyOf(analyses);
            analysisFingerprint = analysisFingerprint == null
                    ? new AnalysisCacheStore.CacheFingerprint("", Map.of())
                    : analysisFingerprint;
            missReasons = List.copyOf(missReasons);
        }
    }

    private record LoadedBaselineEntries(Set<BaselineEntry> entries, Map<BaselineEntry, String> moduleScopeByEntry) {

        private LoadedBaselineEntries {
            entries = Set.copyOf(entries);
            moduleScopeByEntry = Map.copyOf(moduleScopeByEntry);
        }

        private static LoadedBaselineEntries empty() {
            return new LoadedBaselineEntries(Set.of(), Map.of());
        }
    }

    private record FileAnalysisResult(
            String moduleId,
            String relativePath,
            String absolutePathKey,
            List<LintIssue> baselineCandidates,
            List<SourceParseProblem> parseProblems,
            long suppressedIssueCount,
            long cachedFileCount,
            long analyzedFileCount,
            long parseProblemFileCount,
            long analysisNanos,
            long analyzedNanos,
            long cachedNanos,
            CachedFileAnalysis cacheEntry
    ) {

        private FileAnalysisResult {
            baselineCandidates = List.copyOf(baselineCandidates);
            parseProblems = List.copyOf(parseProblems);
        }

        private long sourceFileCount() {
            return 1;
        }
    }
}
