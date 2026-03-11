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
import java.util.List;
import java.util.Map;

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
            java.io.File reportDirectory,
            java.io.File baselineFile,
            java.io.File cacheFile,
            boolean honorInlineSuppressions,
            boolean applyBaseline,
            boolean useIncrementalCache,
            boolean splitBaselineByModule,
            boolean splitCacheByModule,
            boolean scanReactorModules,
            boolean includeTestSourceRoots,
            String enabledRules,
            String disabledRules,
            String enabledRuleDomains,
            String disabledRuleDomains,
            String severityOverrides
    ) throws MojoExecutionException {
        Path projectRoot = projectBaseDir.toPath();
        Path reportsRoot = reportDirectory.toPath();
        Path baselinePath = baselineFile == null ? null : baselineFile.toPath();
        Path analysisCachePath = cacheFile == null ? null : cacheFile.toPath();
        java.util.Set<String> parsedEnabledRules = optionParser.parseRuleIds(enabledRules);
        java.util.Set<String> parsedDisabledRules = optionParser.parseRuleIds(disabledRules);
        java.util.Set<RuleDomain> parsedEnabledRuleDomains = optionParser.parseRuleDomains(enabledRuleDomains);
        java.util.Set<RuleDomain> parsedDisabledRuleDomains = optionParser.parseRuleDomains(disabledRuleDomains);

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
                moduleBaselineFiles,
                moduleCacheFiles,
                parsedEnabledRuleDomains,
                parsedDisabledRuleDomains,
                parsedEnabledRules,
                parsedDisabledRules
        );

        return new MojoExecutionPlan(projectRoot, reportsRoot, baselinePath, sourceRoots, moduleBaselineFiles, rules, options);
    }

    MojoOptionParser optionParser() {
        return optionParser;
    }
}
