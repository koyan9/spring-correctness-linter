/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import io.github.koyan9.linter.core.RuleDomain;

import java.util.List;

record BuiltInRuleGroup(
        RuleDomain domain,
        List<String> ruleIds
) {

    BuiltInRuleGroup {
        ruleIds = List.copyOf(ruleIds);
    }
}
