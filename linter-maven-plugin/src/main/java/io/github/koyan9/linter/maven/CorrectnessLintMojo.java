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
import io.github.koyan9.linter.core.ReportWriter;
import io.github.koyan9.linter.core.rules.SpringBootRuleSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Mojo(name = "lint", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CorrectnessLintMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private java.io.File projectBaseDir;

    @Parameter(defaultValue = "${project.basedir}/src/main/java")
    private java.io.File sourceDirectory;

    @Parameter(defaultValue = "${project.build.directory}/medical-linter")
    private java.io.File reportDirectory;

    @Parameter(defaultValue = "${project.basedir}/medical-linter-baseline.txt")
    private java.io.File baselineFile;

    @Parameter
    private Set<String> formats = new LinkedHashSet<>(Set.of("json", "html", "sarif"));

    @Parameter(defaultValue = "true")
    private boolean honorInlineSuppressions;

    @Parameter(defaultValue = "true")
    private boolean applyBaseline;

    @Parameter(defaultValue = "false")
    private boolean writeBaseline;

    @Parameter(defaultValue = "true")
    private boolean writeBaselineDiff;

    @Parameter(defaultValue = "true")
    private boolean writeRuleDocs;

    @Parameter(defaultValue = "rules-reference.md")
    private String ruleDocsFileName;

    @Parameter(defaultValue = "false")
    private boolean failOnError;

    @Parameter
    private String failOnSeverity;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path projectRoot = projectBaseDir.toPath();
        Path sourceRoot = sourceDirectory.toPath();
        Path reportsRoot = reportDirectory.toPath();
        Path baselinePath = baselineFile == null ? null : baselineFile.toPath();

        try {
            ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
            LintOptions options = new LintOptions(honorInlineSuppressions, applyBaseline, baselinePath);
            LintAnalysisResult result = linter.analyze(projectRoot, sourceRoot, options);
            LintReport report = result.report();

            if (writeBaseline) {
                new BaselineStore().write(baselinePath, projectRoot, result.baselineCandidates());
                getLog().info("Updated baseline file: " + baselinePath);
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
            }
            if (writeRuleDocs) {
                writer.writeRulesMarkdown(report.rules(), reportsRoot.resolve(ruleDocsFileName));
            }

            getLog().info("spring-correctness-linter finished with " + report.issueCount()
                    + " visible issue(s), " + report.suppressedIssueCount() + " inline suppression(s), "
                    + report.baselineMatchedIssueCount() + " baseline match(es), and "
                    + report.staleBaselineEntryCount() + " stale baseline entry(s)");

            LintSeverity threshold = parseSeverity(failOnSeverity);
            if (threshold != null && QualityGate.shouldFail(report, threshold)) {
                throw new MojoFailureException("Lint quality gate failed at severity " + threshold + ". See reports in " + reportsRoot);
            }
            if (threshold == null && failOnError && report.issueCount() > 0) {
                throw new MojoFailureException("Lint issues detected. See reports in " + reportsRoot);
            }
        } catch (IOException exception) {
            throw new MojoExecutionException("Failed to execute spring-correctness-linter", exception);
        }
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