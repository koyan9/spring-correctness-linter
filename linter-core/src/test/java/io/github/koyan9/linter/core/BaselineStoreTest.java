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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsBaselineEntries() throws Exception {
        BaselineStore store = new BaselineStore();
        Path baselineFile = tempDir.resolve("spring-correctness-linter-baseline.txt");
        Path projectRoot = tempDir;
        Path sourceFile = tempDir.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "class Sample {}\n");

        List<LintIssue> issues = List.of(
                new LintIssue("RULE_B", LintSeverity.WARNING, "message-b", sourceFile, 8),
                new LintIssue("RULE_A", LintSeverity.WARNING, "message-a", sourceFile, 3)
        );

        store.write(baselineFile, projectRoot, issues);
        Set<BaselineEntry> loaded = store.load(baselineFile);

        assertEquals(2, loaded.size());
        assertTrue(loaded.contains(new BaselineEntry("RULE_A", "src/main/java/demo/Sample.java", 3, "message-a")));
        assertTrue(loaded.contains(new BaselineEntry("RULE_B", "src/main/java/demo/Sample.java", 8, "message-b")));
    }

    @Test
    void ignoresMalformedBaselineLines() throws Exception {
        BaselineStore store = new BaselineStore();
        Path baselineFile = tempDir.resolve("spring-correctness-linter-baseline.txt");
        Files.writeString(baselineFile, """
                # spring-correctness-linter baseline v1
                broken-line
                RULE_A	bad-number	cGF0aA	bWVzc2FnZQ
                RULE_B	9	c3JjL01haW4uamF2YQ	b2s
                """);

        Set<BaselineEntry> loaded = store.load(baselineFile);

        assertEquals(1, loaded.size());
        assertTrue(loaded.contains(new BaselineEntry("RULE_B", "src/Main.java", 9, "ok")));
    }
}
