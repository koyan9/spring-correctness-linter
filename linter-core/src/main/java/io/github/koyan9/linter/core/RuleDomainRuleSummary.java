/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.util.List;

public record RuleDomainRuleSummary(
        RuleDomain domain,
        List<String> ruleIds
) {

    public RuleDomainRuleSummary {
        domain = domain == null ? RuleDomain.GENERAL : domain;
        ruleIds = ruleIds == null
                ? List.of()
                : ruleIds.stream()
                        .distinct()
                        .sorted()
                        .toList();
    }

    public int ruleCount() {
        return ruleIds.size();
    }
}
