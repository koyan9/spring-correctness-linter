/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.LintAnalysisResult;
import io.github.koyan9.linter.core.LintReport;
import io.github.koyan9.linter.core.ReportWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

final class MojoReportEmitter {

    private final ReportWriter reportWriter = new ReportWriter();

    void write(
            LintAnalysisResult result,
            LintReport report,
            Path reportsRoot,
            Set<String> formats,
            boolean writeBaselineDiff,
            boolean writeRuleDocs,
            String ruleDocsFileName
    ) throws IOException {
        if (formats.contains("json")) {
            reportWriter.writeJson(report, reportsRoot.resolve("lint-report.json"));
        }
        if (formats.contains("html")) {
            reportWriter.writeHtml(report, reportsRoot.resolve("lint-report.html"));
        }
        if (formats.contains("sarif")) {
            reportWriter.writeSarif(report, reportsRoot.resolve("lint-report.sarif.json"));
        }
        if (writeBaselineDiff) {
            reportWriter.writeBaselineDiff(result.baselineDiffReport(), reportsRoot.resolve("baseline-diff.json"));
            reportWriter.writeBaselineDiffHtml(result.baselineDiffReport(), reportsRoot.resolve("baseline-diff.html"));
        }
        if (writeRuleDocs) {
            reportWriter.writeRulesMarkdown(report.rules(), reportsRoot.resolve(ruleDocsFileName));
        }
    }
}
