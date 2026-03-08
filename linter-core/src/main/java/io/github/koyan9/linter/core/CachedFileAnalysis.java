/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.nio.file.Path;
import java.util.List;

public record CachedFileAnalysis(
        String moduleId,
        String relativePath,
        String contentHash,
        List<CachedIssue> issues,
        long suppressedIssueCount,
        List<String> parseProblemMessages
) {

    public CachedFileAnalysis {
        issues = List.copyOf(issues);
        parseProblemMessages = List.copyOf(parseProblemMessages);
    }

    public static CachedFileAnalysis from(
            Path projectRoot,
            SourceDocument sourceDocument,
            List<LintIssue> issues,
            long suppressedIssueCount,
            List<String> parseProblemMessages
    ) {
        return new CachedFileAnalysis(
                sourceDocument.moduleId(),
                sourceDocument.relativePath(projectRoot),
                sourceDocument.contentHash(),
                issues.stream()
                        .map(issue -> new CachedIssue(issue.ruleId(), issue.severity(), issue.line(), issue.message()))
                        .toList(),
                suppressedIssueCount,
                parseProblemMessages
        );
    }

    public List<LintIssue> restoreIssues(Path projectRoot) {
        Path filePath = projectRoot.resolve(relativePath).normalize();
        return issues.stream()
                .map(issue -> new LintIssue(issue.ruleId(), issue.severity(), issue.message(), filePath, issue.line()))
                .toList();
    }

    public SourceParseProblem toParseProblem(Path projectRoot) {
        return new SourceParseProblem(projectRoot.resolve(relativePath).normalize(), parseProblemMessages);
    }

    public record CachedIssue(String ruleId, LintSeverity severity, int line, String message) {
    }
}
