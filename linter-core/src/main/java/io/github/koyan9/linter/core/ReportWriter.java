/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReportWriter {

    private final LintReportJsonWriter lintReportJsonWriter = new LintReportJsonWriter();
    private final LintReportHtmlWriter lintReportHtmlWriter = new LintReportHtmlWriter();
    private final SarifReportWriter sarifReportWriter = new SarifReportWriter();
    private final BaselineDiffJsonWriter baselineDiffJsonWriter = new BaselineDiffJsonWriter();
    private final BaselineDiffHtmlWriter baselineDiffHtmlWriter = new BaselineDiffHtmlWriter();
    private final RulesMarkdownWriter rulesMarkdownWriter = new RulesMarkdownWriter();
    private final RuleGovernanceJsonWriter ruleGovernanceJsonWriter = new RuleGovernanceJsonWriter();

    public void writeJson(LintReport report, Path outputFile) throws IOException {
        ReportWriterSupport.ensureParentDirectory(outputFile);
        Files.writeString(outputFile, lintReportJsonWriter.write(report));
    }

    public void writeHtml(LintReport report, Path outputFile) throws IOException {
        ReportWriterSupport.ensureParentDirectory(outputFile);
        Files.writeString(outputFile, lintReportHtmlWriter.write(report));
    }

    public void writeSarif(LintReport report, Path outputFile) throws IOException {
        ReportWriterSupport.ensureParentDirectory(outputFile);
        Files.writeString(outputFile, sarifReportWriter.write(report));
    }

    public void writeBaselineDiff(BaselineDiffReport baselineDiffReport, Path outputFile) throws IOException {
        ReportWriterSupport.ensureParentDirectory(outputFile);
        Files.writeString(outputFile, baselineDiffJsonWriter.write(baselineDiffReport));
    }

    public void writeBaselineDiffHtml(BaselineDiffReport baselineDiffReport, Path outputFile) throws IOException {
        ReportWriterSupport.ensureParentDirectory(outputFile);
        Files.writeString(outputFile, baselineDiffHtmlWriter.write(baselineDiffReport));
    }

    public void writeRulesMarkdown(Iterable<RuleDescriptor> rules, Path outputFile) throws IOException {
        ReportWriterSupport.ensureParentDirectory(outputFile);
        Files.writeString(outputFile, rulesMarkdownWriter.write(rules));
    }

    public void writeRuleGovernance(LintReport report, Path outputFile) throws IOException {
        ReportWriterSupport.ensureParentDirectory(outputFile);
        Files.writeString(outputFile, ruleGovernanceJsonWriter.write(report));
    }
}
