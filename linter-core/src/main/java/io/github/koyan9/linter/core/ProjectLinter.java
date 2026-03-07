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
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectLinter {

    private final List<LintRule> rules;

    public ProjectLinter(List<LintRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public LintReport analyze(Path projectRoot, Path sourceDirectory) throws IOException {
        return analyze(projectRoot, sourceDirectory, LintOptions.defaults()).report();
    }

    public LintAnalysisResult analyze(Path projectRoot, Path sourceDirectory, LintOptions options) throws IOException {
        ProjectContext context = ProjectContext.load(projectRoot, sourceDirectory);
        Map<Path, InlineSuppressions> suppressions = loadSuppressions(context, options);
        List<LintIssue> rawIssues = new ArrayList<>();

        for (SourceUnit sourceUnit : context.sourceUnits()) {
            for (LintRule rule : rules) {
                rawIssues.addAll(rule.evaluate(sourceUnit, context));
            }
        }

        List<LintIssue> baselineCandidates = new ArrayList<>();
        long suppressedIssueCount = 0;
        for (LintIssue issue : rawIssues) {
            InlineSuppressions inlineSuppressions = suppressions.getOrDefault(issue.file(), InlineSuppressions.none());
            if (inlineSuppressions.suppresses(issue)) {
                suppressedIssueCount++;
                continue;
            }
            baselineCandidates.add(issue);
        }

        Set<BaselineEntry> baselineEntries = loadBaselineEntries(options);
        Set<BaselineEntry> matchedEntries = new HashSet<>();
        List<LintIssue> visibleIssues = new ArrayList<>();
        long baselineMatchedIssueCount = 0;
        for (LintIssue issue : baselineCandidates) {
            BaselineEntry entry = BaselineEntry.from(issue, context.projectRoot());
            if (baselineEntries.contains(entry)) {
                baselineMatchedIssueCount++;
                matchedEntries.add(entry);
                continue;
            }
            visibleIssues.add(issue);
        }

        Set<BaselineEntry> staleEntries = new HashSet<>(baselineEntries);
        staleEntries.removeAll(matchedEntries);

        List<RuleDescriptor> descriptors = rules.stream()
                .map(rule -> new RuleDescriptor(rule.id(), rule.title(), rule.description(), rule.severity()))
                .toList();

        LintReport report = new LintReport(
                context.projectRoot(),
                context.sourceDirectory(),
                Instant.now(),
                descriptors,
                visibleIssues,
                suppressedIssueCount,
                baselineMatchedIssueCount,
                staleEntries.size()
        );
        return new LintAnalysisResult(
                report,
                baselineCandidates,
                new BaselineDiffReport(visibleIssues, matchedEntries, staleEntries)
        );
    }

    private Map<Path, InlineSuppressions> loadSuppressions(ProjectContext context, LintOptions options) {
        if (!options.honorInlineSuppressions()) {
            return Map.of();
        }

        Map<Path, InlineSuppressions> suppressions = new HashMap<>();
        for (SourceUnit sourceUnit : context.sourceUnits()) {
            suppressions.put(sourceUnit.path(), InlineSuppressions.parse(sourceUnit));
        }
        return suppressions;
    }

    private Set<BaselineEntry> loadBaselineEntries(LintOptions options) throws IOException {
        if (!options.applyBaseline() || options.baselineFile() == null) {
            return Set.of();
        }
        return new BaselineStore().load(options.baselineFile());
    }
}