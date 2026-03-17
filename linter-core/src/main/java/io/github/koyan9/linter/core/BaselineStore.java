/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BaselineStore {

    private static final String HEADER = "# spring-correctness-linter baseline v1";

    public Set<BaselineEntry> load(Path baselineFile) throws IOException {
        if (baselineFile == null || !Files.exists(baselineFile)) {
            return Set.of();
        }

        Set<BaselineEntry> entries = new LinkedHashSet<>();
        try (java.io.BufferedReader reader = Files.newBufferedReader(baselineFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                BaselineEntry entry = parseEntry(line);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    public void write(Path baselineFile, Path projectRoot, List<LintIssue> issues) throws IOException {
        if (baselineFile == null) {
            return;
        }

        Path parent = baselineFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> lines = issues.stream()
                .map(issue -> BaselineEntry.from(issue, projectRoot))
                .sorted(Comparator.comparing(BaselineEntry::relativePath)
                        .thenComparingInt(BaselineEntry::line)
                        .thenComparing(BaselineEntry::ruleId))
                .map(this::toLine)
                .toList();

        StringBuilder content = new StringBuilder();
        content.append(HEADER).append(System.lineSeparator());
        for (String line : lines) {
            content.append(line).append(System.lineSeparator());
        }
        Files.writeString(baselineFile, content.toString());
    }

    private BaselineEntry parseEntry(String line) {
        String[] parts = line.split("\t", 4);
        if (parts.length != 4) {
            return null;
        }

        try {
            return new BaselineEntry(
                    parts[0],
                    decode(parts[2]),
                    Integer.parseInt(parts[1]),
                    decode(parts[3])
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String toLine(BaselineEntry entry) {
        return entry.ruleId()
                + "\t" + entry.line()
                + "\t" + encode(entry.relativePath())
                + "\t" + encode(entry.message());
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
