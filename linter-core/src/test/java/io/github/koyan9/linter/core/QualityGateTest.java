/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityGateTest {

    @Test
    void failsWhenIssueMeetsThreshold() {
        LintReport report = new LintReport(
                Path.of("."),
                Path.of("src/main/java"),
                Instant.now(),
                List.of(),
                List.of(new LintIssue("RULE", LintSeverity.WARNING, "message", Path.of("Demo.java"), 1)),
                0,
                0,
                0
        );

        assertTrue(QualityGate.shouldFail(report, LintSeverity.WARNING));
        assertFalse(QualityGate.shouldFail(report, LintSeverity.ERROR));
    }

    @Test
    void doesNotFailWithoutThreshold() {
        LintReport report = new LintReport(
                Path.of("."),
                Path.of("src/main/java"),
                Instant.now(),
                List.of(),
                List.of(new LintIssue("RULE", LintSeverity.ERROR, "message", Path.of("Demo.java"), 1)),
                0,
                0,
                0
        );

        assertFalse(QualityGate.shouldFail(report, null));
    }
}