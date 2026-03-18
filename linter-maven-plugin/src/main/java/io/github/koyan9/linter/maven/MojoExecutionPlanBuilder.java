/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.LintOptions;
import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.RuleSelection;
import io.github.koyan9.linter.core.SourceRoot;
import io.github.koyan9.linter.core.rules.SpringBootRuleSet;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MojoExecutionPlanBuilder {

    private final MojoOptionParser optionParser = new MojoOptionParser();
    private final MojoSourceRootResolver sourceRootResolver = new MojoSourceRootResolver();
    private final MojoPathStrategy pathStrategy = new MojoPathStrategy();

    MojoExecutionPlan build(
            java.io.File projectBaseDir,
            MavenProject project,
            List<MavenProject> reactorProjects,
            java.io.File sourceDirectory,
            String additionalSourceDirectories,
            String moduleSourceDirectories,
            java.io.File reportDirectory,
            java.io.File baselineFile,
            java.io.File cacheFile,
            boolean honorInlineSuppressions,
            boolean applyBaseline,
            boolean useIncrementalCache,
            boolean parallelFileAnalysis,
            int fileAnalysisParallelism,
            boolean splitBaselineByModule,
            boolean splitCacheByModule,
            boolean scanReactorModules,
            boolean includeTestSourceRoots,
            String enabledRules,
            String disabledRules,
            String enabledRuleDomains,
            String disabledRuleDomains,
            String severityOverrides,
            boolean assumeCentralizedSecurity,
            boolean autoDetectCentralizedSecurity,
            String securityAnnotations,
            String cacheDefaultKeyCacheNames
    ) throws MojoExecutionException {
        Path projectRoot = projectBaseDir.toPath();
        Path reportsRoot = reportDirectory.toPath();
        Path baselinePath = baselineFile == null ? null : baselineFile.toPath();
        Path analysisCachePath = cacheFile == null ? null : cacheFile.toPath();
        java.util.Set<String> parsedEnabledRules = optionParser.parseRuleIds(enabledRules);
        java.util.Set<String> parsedDisabledRules = optionParser.parseRuleIds(disabledRules);
        java.util.Set<RuleDomain> parsedEnabledRuleDomains = optionParser.parseRuleDomains(enabledRuleDomains);
        java.util.Set<RuleDomain> parsedDisabledRuleDomains = optionParser.parseRuleDomains(disabledRuleDomains);
        Set<String> parsedSecurityAnnotations = normalizeAnnotationNames(optionParser.parseStringSet(securityAnnotations));
        Set<String> parsedCacheDefaultKeyCacheNames = optionParser.parseStringSet(cacheDefaultKeyCacheNames);
        Map<String, List<String>> parsedModuleSourceDirectories = optionParser.parseModuleSourceDirectories(moduleSourceDirectories);
        validateModuleSourceDirectories(parsedModuleSourceDirectories, project, reactorProjects, scanReactorModules);

        List<LintRule> rules = RuleSelection.configure(
                SpringBootRuleSet.defaultRules(),
                parsedEnabledRules,
                parsedDisabledRules,
                parsedEnabledRuleDomains,
                parsedDisabledRuleDomains,
                optionParser.parseSeverityOverrides(severityOverrides)
        );
        List<SourceRoot> sourceRoots = sourceRootResolver.resolve(
                projectRoot,
                project,
                reactorProjects,
                sourceDirectory,
                additionalSourceDirectories,
                parsedModuleSourceDirectories,
                scanReactorModules,
                includeTestSourceRoots
        );
        Map<String, Path> moduleBaselineFiles = splitBaselineByModule
                ? pathStrategy.resolveModuleScopedPaths(sourceRoots, baselinePath)
                : Map.of();
        Map<String, Path> moduleCacheFiles = splitCacheByModule
                ? pathStrategy.resolveModuleScopedPaths(sourceRoots, analysisCachePath)
                : Map.of();

        LintOptions options = new LintOptions(
                honorInlineSuppressions,
                applyBaseline,
                splitBaselineByModule ? null : baselinePath,
                splitCacheByModule ? null : analysisCachePath,
                useIncrementalCache,
                parallelFileAnalysis,
                fileAnalysisParallelism,
                moduleBaselineFiles,
                moduleCacheFiles,
                parsedEnabledRuleDomains,
                parsedDisabledRuleDomains,
                parsedEnabledRules,
                parsedDisabledRules,
                assumeCentralizedSecurity,
                autoDetectCentralizedSecurity,
                parsedSecurityAnnotations,
                parsedCacheDefaultKeyCacheNames
        );

        return new MojoExecutionPlan(projectRoot, reportsRoot, baselinePath, sourceRoots, moduleBaselineFiles, rules, options);
    }

    MojoOptionParser optionParser() {
        return optionParser;
    }

    private Set<String> normalizeAnnotationNames(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.startsWith("@")) {
                trimmed = trimmed.substring(1).trim();
            }
            int dotIndex = trimmed.lastIndexOf('.');
            normalized.add(dotIndex >= 0 ? trimmed.substring(dotIndex + 1) : trimmed);
        }
        return normalized;
    }

    private void validateModuleSourceDirectories(
            Map<String, List<String>> moduleSourceDirectories,
            MavenProject project,
            List<MavenProject> reactorProjects,
            boolean scanReactorModules
    ) throws MojoExecutionException {
        if (moduleSourceDirectories == null || moduleSourceDirectories.isEmpty()) {
            return;
        }
        Map<String, MavenProject> projectsByModuleId = new LinkedHashMap<>();
        List<MavenProject> projectsToScan = scanReactorModules
                ? (reactorProjects == null || reactorProjects.isEmpty() ? List.of(project) : reactorProjects)
                : List.of(project);
        for (MavenProject current : projectsToScan) {
            if (current == null) {
                continue;
            }
            projectsByModuleId.putIfAbsent(moduleId(current), current);
        }
        Set<String> unknown = new LinkedHashSet<>(moduleSourceDirectories.keySet());
        unknown.removeAll(projectsByModuleId.keySet());
        if (!unknown.isEmpty()) {
            String available = projectsByModuleId.keySet().stream().sorted().collect(java.util.stream.Collectors.joining(", "));
            throw new MojoExecutionException("Unknown module id(s) in moduleSourceDirectories: "
                    + String.join(", ", unknown) + ". Available modules: " + available + ".");
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
}
