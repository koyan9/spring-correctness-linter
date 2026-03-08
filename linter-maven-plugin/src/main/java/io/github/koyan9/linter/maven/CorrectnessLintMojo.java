/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.BaselineStore;
import io.github.koyan9.linter.core.LintAnalysisResult;
import io.github.koyan9.linter.core.LintOptions;
import io.github.koyan9.linter.core.LintReport;
import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.LintSeverity;
import io.github.koyan9.linter.core.ProjectLinter;
import io.github.koyan9.linter.core.QualityGate;
import io.github.koyan9.linter.core.ReportWriter;
import io.github.koyan9.linter.core.RuleSelection;
import io.github.koyan9.linter.core.SourceRoot;
import io.github.koyan9.linter.core.rules.SpringBootRuleSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Mojo(name = "lint", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CorrectnessLintMojo extends AbstractMojo {

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

    @Parameter(property = "spring.correctness.linter.severityOverrides")
    private String severityOverrides;

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
        Path projectRoot = projectBaseDir.toPath();
        Path reportsRoot = reportDirectory.toPath();
        Path baselinePath = baselineFile == null ? null : baselineFile.toPath();
        Path analysisCachePath = cacheFile == null ? null : cacheFile.toPath();

        try {
            if (scanReactorModules && project != null && !project.isExecutionRoot()) {
                getLog().info("Skipping module because spring.correctness.linter.scanReactorModules=true and current project is not the execution root.");
                return;
            }

            List<LintRule> rules = RuleSelection.configure(
                    SpringBootRuleSet.defaultRules(),
                    parseRuleIds(enabledRules),
                    parseRuleIds(disabledRules),
                    parseSeverityOverrides(severityOverrides)
            );
            List<SourceRoot> sourceRoots = resolveSourceRoots(projectRoot);
            Map<String, Path> moduleBaselineFiles = splitBaselineByModule ? resolveModuleScopedPaths(sourceRoots, baselinePath) : Map.of();
            Map<String, Path> moduleCacheFiles = splitCacheByModule ? resolveModuleScopedPaths(sourceRoots, analysisCachePath) : Map.of();
            ProjectLinter linter = new ProjectLinter(rules);
            LintOptions options = new LintOptions(
                    honorInlineSuppressions,
                    applyBaseline,
                    splitBaselineByModule ? null : baselinePath,
                    splitCacheByModule ? null : analysisCachePath,
                    useIncrementalCache,
                    moduleBaselineFiles,
                    moduleCacheFiles
            );
            LintAnalysisResult result = linter.analyzeSourceRoots(projectRoot, sourceRoots, options);
            LintReport report = result.report();

            if (writeBaseline) {
                writeBaselineFiles(projectRoot, result, report, baselinePath, moduleBaselineFiles);
            }

            ReportWriter writer = new ReportWriter();
            if (formats.contains("json")) {
                writer.writeJson(report, reportsRoot.resolve("lint-report.json"));
            }
            if (formats.contains("html")) {
                writer.writeHtml(report, reportsRoot.resolve("lint-report.html"));
            }
            if (formats.contains("sarif")) {
                writer.writeSarif(report, reportsRoot.resolve("lint-report.sarif.json"));
            }
            if (writeBaselineDiff && baselinePath != null) {
                writer.writeBaselineDiff(result.baselineDiffReport(), reportsRoot.resolve("baseline-diff.json"));
                writer.writeBaselineDiffHtml(result.baselineDiffReport(), reportsRoot.resolve("baseline-diff.html"));
            }
            if (writeRuleDocs) {
                writer.writeRulesMarkdown(report.rules(), reportsRoot.resolve(ruleDocsFileName));
            }

            getLog().info("spring-correctness-linter finished with " + report.issueCount()
                    + " visible issue(s), " + report.suppressedIssueCount() + " inline suppression(s), "
                    + report.baselineMatchedIssueCount() + " baseline match(es), and "
                    + report.staleBaselineEntryCount() + " stale baseline entry(s)");
            if (report.parseProblemFileCount() > 0) {
                getLog().warn("spring-correctness-linter observed parse problems in "
                        + report.parseProblemFileCount() + " source file(s); findings may be incomplete. Check generated reports for details.");
            }
            if (report.cachedFileCount() > 0) {
                getLog().info("spring-correctness-linter reused incremental cache for "
                        + report.cachedFileCount() + " source file(s)");
            }

            LintSeverity threshold = parseSeverity(failOnSeverity);
            if (threshold != null && QualityGate.shouldFail(report, threshold)) {
                Set<String> failingModules = QualityGate.failingModules(report, threshold);
                String moduleMessage = failingModules.isEmpty()
                        ? ""
                        : " Failing modules: " + failingModules.stream().collect(Collectors.joining(", ")) + ".";
                throw new MojoFailureException("Lint quality gate failed at severity " + threshold + "." + moduleMessage + " See reports in " + reportsRoot);
            }
            if (threshold == null && failOnError && report.issueCount() > 0) {
                Set<String> failingModules = report.issues().stream()
                        .map(issue -> report.moduleFor(issue.file()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                String moduleMessage = failingModules.isEmpty()
                        ? ""
                        : " Failing modules: " + failingModules.stream().collect(Collectors.joining(", ")) + ".";
                throw new MojoFailureException("Lint issues detected." + moduleMessage + " See reports in " + reportsRoot);
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new MojoExecutionException("Failed to execute spring-correctness-linter", exception);
        }
    }

    private Set<String> parseRuleIds(String value) {
        Set<String> ruleIds = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return ruleIds;
        }

        for (String token : value.split("[,;]")) {
            if (!token.isBlank()) {
                ruleIds.add(token.trim());
            }
        }
        return ruleIds;
    }

    private List<SourceRoot> resolveSourceRoots(Path projectRoot) {
        LinkedHashMap<Path, SourceRoot> sourceRoots = new LinkedHashMap<>();

        if (scanReactorModules) {
            List<MavenProject> projectsToScan = reactorProjects == null || reactorProjects.isEmpty()
                    ? List.of(project)
                    : reactorProjects;
            for (MavenProject reactorProject : projectsToScan) {
                addProjectSourceRoots(reactorProject, sourceRoots);
            }
        } else if (project != null) {
            addProjectSourceRoots(project, sourceRoots);
        }

        if (sourceDirectory != null) {
            SourceRoot sourceRoot = new SourceRoot(sourceDirectory.toPath().toAbsolutePath().normalize(), moduleId(project));
            sourceRoots.put(sourceRoot.path(), sourceRoot);
        }
        if (additionalSourceDirectories != null && !additionalSourceDirectories.isBlank()) {
            for (String token : additionalSourceDirectories.split("[,;]")) {
                if (!token.isBlank()) {
                    Path sourceRoot = Path.of(token.trim());
                    Path resolvedPath = (sourceRoot.isAbsolute() ? sourceRoot : projectRoot.resolve(sourceRoot)).toAbsolutePath().normalize();
                    SourceRoot currentSourceRoot = new SourceRoot(resolvedPath, moduleId(project));
                    sourceRoots.put(currentSourceRoot.path(), currentSourceRoot);
                }
            }
        }

        return sourceRoots.values().stream().toList();
    }

    private void addProjectSourceRoots(MavenProject currentProject, Map<Path, SourceRoot> sourceRoots) {
        if (currentProject == null) {
            return;
        }
        addStringRoots(currentProject.getCompileSourceRoots(), sourceRoots, moduleId(currentProject));
        if (includeTestSourceRoots) {
            addStringRoots(currentProject.getTestCompileSourceRoots(), sourceRoots, moduleId(currentProject));
        }
    }

    private void addStringRoots(List<String> roots, Map<Path, SourceRoot> sourceRoots, String moduleId) {
        if (roots == null) {
            return;
        }
        for (String root : roots) {
            if (root != null && !root.isBlank()) {
                Path sourceRootPath = Path.of(root).toAbsolutePath().normalize();
                sourceRoots.put(sourceRootPath, new SourceRoot(sourceRootPath, moduleId));
            }
        }
    }

    private String moduleId(MavenProject currentProject) {
        if (currentProject == null) {
            return ".";
        }
        if (currentProject.getArtifactId() != null && !currentProject.getArtifactId().isBlank()) {
            return currentProject.getArtifactId();
        }
        if (currentProject.getFile() != null && currentProject.getFile().getParentFile() != null) {
            return currentProject.getFile().getParentFile().getName();
        }
        return ".";
    }

    private Map<String, LintSeverity> parseSeverityOverrides(String value) throws MojoExecutionException {
        Map<String, LintSeverity> overrides = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return overrides;
        }

        for (String token : value.split("[,;]")) {
            if (token.isBlank()) {
                continue;
            }

            String[] parts = token.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new MojoExecutionException(
                        "Invalid severityOverrides entry: " + token + ". Expected RULE_ID=INFO|WARNING|ERROR."
                );
            }
            overrides.put(parts[0].trim(), parseSeverity(parts[1].trim()));
        }
        return overrides;
    }

    private Map<String, Path> resolveModuleScopedPaths(List<SourceRoot> sourceRoots, Path basePath) {
        if (basePath == null) {
            return Map.of();
        }
        LinkedHashMap<String, Path> scopedPaths = new LinkedHashMap<>();
        for (SourceRoot sourceRoot : sourceRoots) {
            scopedPaths.putIfAbsent(sourceRoot.moduleId(), moduleScopedPath(basePath, sourceRoot.moduleId()));
        }
        return scopedPaths;
    }

    private Path moduleScopedPath(Path basePath, String moduleId) {
        Path normalizedBasePath = basePath.toAbsolutePath().normalize();
        Path parent = normalizedBasePath.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }
        String sanitizedModuleId = sanitizeModuleId(moduleId);
        return parent.resolve("modules").resolve(sanitizedModuleId).resolve(normalizedBasePath.getFileName().toString());
    }

    private String sanitizeModuleId(String moduleId) {
        if (moduleId == null || moduleId.isBlank() || ".".equals(moduleId)) {
            return "root";
        }
        return moduleId.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private void writeBaselineFiles(
            Path projectRoot,
            LintAnalysisResult result,
            LintReport report,
            Path baselinePath,
            Map<String, Path> moduleBaselineFiles
    ) throws IOException {
        BaselineStore baselineStore = new BaselineStore();
        if (!moduleBaselineFiles.isEmpty()) {
            for (Map.Entry<String, Path> entry : moduleBaselineFiles.entrySet()) {
                List<io.github.koyan9.linter.core.LintIssue> moduleIssues = result.baselineCandidates().stream()
                        .filter(issue -> entry.getKey().equals(report.moduleFor(issue.file())))
                        .toList();
                baselineStore.write(entry.getValue(), projectRoot, moduleIssues);
                getLog().info("Updated module baseline file for " + entry.getKey() + ": " + entry.getValue());
            }
            return;
        }
        baselineStore.write(baselinePath, projectRoot, result.baselineCandidates());
        getLog().info("Updated baseline file: " + baselinePath);
    }

    private LintSeverity parseSeverity(String value) throws MojoExecutionException {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LintSeverity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new MojoExecutionException("Invalid failOnSeverity value: " + value + ". Expected INFO, WARNING, or ERROR.", exception);
        }
    }
}
