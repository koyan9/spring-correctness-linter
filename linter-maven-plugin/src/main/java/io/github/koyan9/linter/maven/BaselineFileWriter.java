/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.BaselineStore;
import io.github.koyan9.linter.core.LintAnalysisResult;
import io.github.koyan9.linter.core.LintReport;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class BaselineFileWriter {

    void write(
            Path projectRoot,
            LintAnalysisResult result,
            LintReport report,
            Path baselinePath,
            Map<String, Path> moduleBaselineFiles,
            Log log
    ) throws IOException {
        BaselineStore baselineStore = new BaselineStore();
        if (!moduleBaselineFiles.isEmpty()) {
            for (Map.Entry<String, Path> entry : moduleBaselineFiles.entrySet()) {
                List<io.github.koyan9.linter.core.LintIssue> moduleIssues = result.baselineCandidates().stream()
                        .filter(issue -> entry.getKey().equals(report.moduleFor(issue.file())))
                        .toList();
                baselineStore.write(entry.getValue(), projectRoot, moduleIssues);
                log.info("Updated module baseline file for " + entry.getKey() + ": " + entry.getValue());
            }
            return;
        }
        baselineStore.write(baselinePath, projectRoot, result.baselineCandidates());
        log.info("Updated baseline file: " + baselinePath);
    }
}
