/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;

public interface LintRule {

    String id();

    String title();

    String description();

    LintSeverity severity();

    List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context);
}
