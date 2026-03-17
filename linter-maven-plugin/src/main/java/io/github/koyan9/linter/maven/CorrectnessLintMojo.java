/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.BaselineStore;
import io.github.koyan9.linter.core.LintAnalysisResult;
import io.github.koyan9.linter.core.LintOptions;
import io.github.koyan9.linter.core.LintReport;
import io.github.koyan9.linter.core.LintSeverity;
import io.github.koyan9.linter.core.ProjectLinter;
import io.github.koyan9.linter.core.QualityGate;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Mojo(name = "lint", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CorrectnessLintMojo extends AbstractMojo {

    private static final Set<String> ALLOWED_FORMATS = Set.of("json", "html", "sarif");

    private final BaselineFileWriter baselineFileWriter = new BaselineFileWriter();
    private final MojoExecutionPlanBuilder executionPlanBuilder = new MojoExecutionPlanBuilder();
    private final MojoReportEmitter reportEmitter = new MojoReportEmitter();
    private final MojoFailureMessageBuilder failureMessageBuilder = new MojoFailureMessageBuilder();
    private final MojoRuleSelectionSummaryFormatter ruleSelectionSummaryFormatter = new MojoRuleSelectionSummaryFormatter();

    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private java.io.File projectBaseDir;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${reactorProjects}", readonly = true)
    private List<MavenProject> reactorProjects = List.of();

    @Parameter(property = "spring.correctness.linter.sourceDirectory", defaultValue = "${project.basedir}/src/main/java")
    private java.io.File sourceDirectory;

    @Parameter(property = "spring.correctness.linter.additionalSourceDirectories")
    private String additionalSourceDirectories;

    @Parameter(property = "spring.correctness.linter.reportDirectory", defaultValue = "${project.build.directory}/spring-correctness-linter")
    private java.io.File reportDirectory;

    @Parameter(property = "spring.correctness.linter.baselineFile", defaultValue = "${project.basedir}/spring-correctness-linter-baseline.txt")
    private java.io.File baselineFile;

    @Parameter(property = "spring.correctness.linter.formats")
    private Set<String> formats = new LinkedHashSet<>(Set.of("json", "html", "sarif"));

    @Parameter(property = "spring.correctness.linter.honorInlineSuppressions", defaultValue = "true")
    private boolean honorInlineSuppressions;

    @Parameter(property = "spring.correctness.linter.applyBaseline", defaultValue = "true")
    private boolean applyBaseline;

    @Parameter(property = "spring.correctness.linter.writeBaseline", defaultValue = "false")
    private boolean writeBaseline;

    @Parameter(property = "spring.correctness.linter.writeBaselineDiff", defaultValue = "true")
    private boolean writeBaselineDiff;

    @Parameter(property = "spring.correctness.linter.writeRuleDocs", defaultValue = "true")
    private boolean writeRuleDocs;

    @Parameter(property = "spring.correctness.linter.ruleDocsFileName", defaultValue = "rules-reference.md")
    private String ruleDocsFileName;

    @Parameter(property = "spring.correctness.linter.failOnError", defaultValue = "false")
    private boolean failOnError;

    @Parameter(property = "spring.correctness.linter.failOnSeverity")
    private String failOnSeverity;

    @Parameter(property = "spring.correctness.linter.enabledRules")
    private String enabledRules;

    @Parameter(property = "spring.correctness.linter.disabledRules")
    private String disabledRules;

    @Parameter(property = "spring.correctness.linter.enabledRuleDomains")
    private String enabledRuleDomains;

    @Parameter(property = "spring.correctness.linter.disabledRuleDomains")
    private String disabledRuleDomains;

    @Parameter(property = "spring.correctness.linter.severityOverrides")
    private String severityOverrides;

    @Parameter(property = "spring.correctness.linter.assumeCentralizedSecurity", defaultValue = "false")
    private boolean assumeCentralizedSecurity;

    @Parameter(property = "spring.correctness.linter.securityAnnotations")
    private String securityAnnotations;

    @Parameter(property = "spring.correctness.linter.cacheDefaultKeyCacheNames")
    private String cacheDefaultKeyCacheNames;

    @Parameter(property = "spring.correctness.linter.cacheFile", defaultValue = "${project.build.directory}/spring-correctness-linter/analysis-cache.txt")
    private java.io.File cacheFile;

    @Parameter(property = "spring.correctness.linter.useIncrementalCache", defaultValue = "true")
    private boolean useIncrementalCache;

    @Parameter(property = "spring.correctness.linter.splitBaselineByModule", defaultValue = "false")
    private boolean splitBaselineByModule;

    @Parameter(property = "spring.correctness.linter.splitCacheByModule", defaultValue = "false")
    private boolean splitCacheByModule;

    @Parameter(property = "spring.correctness.linter.scanReactorModules", defaultValue = "false")
    private boolean scanReactorModules;

    @Parameter(property = "spring.correctness.linter.includeTestSourceRoots", defaultValue = "false")
    private boolean includeTestSourceRoots;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (scanReactorModules && project != null && !project.isExecutionRoot()) {
                getLog().info("Skipping module because spring.correctness.linter.scanReactorModules=true and current project is not the execution root.");
                return;
            }

            MojoExecutionPlan plan = executionPlanBuilder.build(
                    projectBaseDir,
                    project,
                    reactorProjects,
                    sourceDirectory,
                    additionalSourceDirectories,
                    reportDirectory,
                    baselineFile,
                    cacheFile,
                    honorInlineSuppressions,
                    applyBaseline,
                    useIncrementalCache,
                    splitBaselineByModule,
                    splitCacheByModule,
                    scanReactorModules,
                    includeTestSourceRoots,
                    enabledRules,
                    disabledRules,
                    enabledRuleDomains,
                    disabledRuleDomains,
                    severityOverrides,
                    assumeCentralizedSecurity,
                    securityAnnotations,
                    cacheDefaultKeyCacheNames
            );
            ProjectLinter linter = new ProjectLinter(plan.rules());
            LintAnalysisResult result = linter.analyzeSourceRoots(plan.projectRoot(), plan.sourceRoots(), plan.options());
            LintReport report = result.report();

            if (writeBaseline) {
                baselineFileWriter.write(plan.projectRoot(), result, report, plan.baselinePath(), plan.moduleBaselineFiles(), getLog());
            }

            Set<String> normalizedFormats = normalizeFormats(formats);
            if (normalizedFormats.isEmpty()) {
                getLog().warn("spring-correctness-linter: no valid report formats configured; skipping core report outputs.");
            }
            reportEmitter.write(
                    result,
                    report,
                    plan.reportsRoot(),
                    normalizedFormats,
                    writeBaselineDiff && plan.baselinePath() != null,
                    writeRuleDocs,
                    resolveRuleDocsFileName(ruleDocsFileName)
            );

            getLog().info("spring-correctness-linter finished with " + report.issueCount()
                    + " visible issue(s), " + report.suppressedIssueCount() + " inline suppression(s), "
                    + report.baselineMatchedIssueCount() + " baseline match(es), and "
                    + report.staleBaselineEntryCount() + " stale baseline entry(s)");
            getLog().info("spring-correctness-linter runtime: " + report.runtimeMetrics().totalElapsedMillis()
                    + " ms across " + report.runtimeMetrics().sourceFileCount() + " source file(s) ("
                    + report.runtimeMetrics().analyzedFileCount() + " analyzed, "
                    + report.runtimeMetrics().cachedFileCount() + " cached)");
            getLog().info("spring-correctness-linter cache: " + report.runtimeMetrics().cacheHitRatePercent()
                    + "% hit rate (" + report.runtimeMetrics().cacheScope() + ")");
            if (report.runtimeMetrics().moduleMetrics().size() > 1) {
                String slowModules = report.runtimeMetrics().slowestModules(5).stream()
                        .map(metric -> metric.moduleId() + "=" + metric.analyzedMillis()
                                + " ms (" + metric.cacheHitRatePercent() + "% cache)")
                        .collect(Collectors.joining(", "));
                if (!slowModules.isBlank()) {
                    getLog().info("spring-correctness-linter slowest analyzed modules: " + slowModules);
                }
            }
            getLog().info(ruleSelectionSummaryFormatter.format(report.ruleDomainSelection()));
            if (report.parseProblemFileCount() > 0) {
                getLog().warn("spring-correctness-linter observed parse problems in "
                        + report.parseProblemFileCount() + " source file(s); findings may be incomplete. Check generated reports for details.");
            }
            if (report.cachedFileCount() > 0) {
                getLog().info("spring-correctness-linter reused incremental cache for "
                        + report.cachedFileCount() + " source file(s)");
            } else {
                logCacheHints(plan.options(), report);
            }

            LintSeverity threshold = executionPlanBuilder.optionParser().parseSeverity(failOnSeverity);
            if (threshold != null && QualityGate.shouldFail(report, threshold)) {
                throw new MojoFailureException(failureMessageBuilder.qualityGateFailure(report, threshold, plan.reportsRoot()));
            }
            if (threshold == null && failOnError && report.issueCount() > 0) {
                throw new MojoFailureException(failureMessageBuilder.visibleIssuesFailure(report, plan.reportsRoot()));
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new MojoExecutionException("Failed to execute spring-correctness-linter", exception);
        }
    }

    private Set<String> normalizeFormats(Set<String> requestedFormats) {
        Set<String> normalized = new LinkedHashSet<>();
        if (requestedFormats == null || requestedFormats.isEmpty()) {
            return normalized;
        }
        for (String format : requestedFormats) {
            if (format == null || format.isBlank()) {
                continue;
            }
            String normalizedFormat = format.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_FORMATS.contains(normalizedFormat)) {
                getLog().warn("spring-correctness-linter: unknown report format '" + format + "'. Supported formats: " + ALLOWED_FORMATS + ".");
                continue;
            }
            normalized.add(normalizedFormat);
        }
        return normalized;
    }

    private String resolveRuleDocsFileName(String value) {
        if (value == null || value.isBlank()) {
            getLog().warn("spring-correctness-linter: ruleDocsFileName is blank; using rules-reference.md");
            return "rules-reference.md";
        }
        return value;
    }

    private void logCacheHints(LintOptions options, LintReport report) {
        if (!options.useIncrementalCache()) {
            return;
        }
        if (report.cachedFileCount() > 0) {
            return;
        }

        if (!options.moduleAnalysisCacheFiles().isEmpty()) {
            int total = options.moduleAnalysisCacheFiles().size();
            long existing = options.moduleAnalysisCacheFiles().values().stream().filter(Files::exists).count();
            if (existing == 0) {
                getLog().info("spring-correctness-linter cache: no module cache files found (" + total + " expected). First run or cache directory not persisted.");
            } else {
                getLog().info("spring-correctness-linter cache: " + existing + "/" + total
                        + " module cache files found, but no entries reused. Likely a fingerprint change or all files modified.");
            }
            return;
        }

        Path cacheFile = options.analysisCacheFile();
        if (cacheFile == null) {
            getLog().info("spring-correctness-linter cache: incremental cache enabled but no cache file configured.");
            return;
        }
        if (!Files.exists(cacheFile)) {
            getLog().info("spring-correctness-linter cache: no cache file found at " + cacheFile + ". First run or cache directory not persisted.");
            return;
        }
        getLog().info("spring-correctness-linter cache: cache file found at " + cacheFile
                + ", but no entries reused. Likely a fingerprint change or all files modified.");
    }
}
