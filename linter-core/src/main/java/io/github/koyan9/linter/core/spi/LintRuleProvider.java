/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.spi;

import io.github.koyan9.linter.core.LintRule;

import java.util.List;

public interface LintRuleProvider {

    List<LintRule> rules();

    default String providerId() {
        return getClass().getName();
    }
}
