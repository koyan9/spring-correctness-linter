/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesGeneratedRuleReferenceMarkdown() throws Exception {
        Path output = tempDir.resolve("reports/rules-reference.md");
        List<RuleDescriptor> rules = List.of(
                new RuleDescriptor("RULE_A", "Rule A", "Description A", LintSeverity.WARNING),
                new RuleDescriptor("RULE_B", "Rule B", "Description B", LintSeverity.ERROR)
        );

        new ReportWriter().writeRulesMarkdown(rules, output);
        String markdown = Files.readString(output);

        assertTrue(markdown.contains("# Generated Rule Reference"));
        assertTrue(markdown.contains("## `RULE_A`"));
        assertTrue(markdown.contains("Severity: `ERROR`"));
    }
}