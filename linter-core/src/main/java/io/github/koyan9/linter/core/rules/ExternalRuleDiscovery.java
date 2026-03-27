/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.spi.LintRuleProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

final class ExternalRuleDiscovery {

    private ExternalRuleDiscovery() {
    }

    static List<LintRule> discover(ClassLoader classLoader, Set<String> reservedRuleIds) {
        if (classLoader == null) {
            return List.of();
        }

        List<LintRule> discoveredRules = new ArrayList<>();
        Set<String> seenRuleIds = new LinkedHashSet<>(reservedRuleIds);

        for (LintRuleProvider provider : ServiceLoader.load(LintRuleProvider.class, classLoader)) {
            List<LintRule> providerRules = provider.rules();
            if (providerRules == null) {
                throw new IllegalStateException("External lint rule provider returned null rule list: " + provider.providerId());
            }
            for (LintRule rule : providerRules) {
                if (rule == null) {
                    throw new IllegalStateException("External lint rule provider returned a null rule instance: " + provider.providerId());
                }
                String ruleId = rule.id();
                if (ruleId == null || ruleId.isBlank()) {
                    throw new IllegalStateException("External lint rule provider returned a rule with a blank id: " + provider.providerId());
                }
                if (!seenRuleIds.add(ruleId)) {
                    throw new IllegalStateException("Duplicate rule id detected while loading external rules: " + ruleId);
                }
                discoveredRules.add(rule);
            }
        }

        return discoveredRules.stream()
                .sorted(java.util.Comparator.comparing(LintRule::id))
                .toList();
    }
}
