/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import io.github.koyan9.linter.core.LintRule;

import java.util.ArrayList;
import java.util.List;

public final class SpringBootRuleSet {

    private SpringBootRuleSet() {
    }

    public static List<LintRule> defaultRules() {
        return defaultRules(Thread.currentThread().getContextClassLoader());
    }

    static List<LintRule> defaultRules(ClassLoader classLoader) {
        List<LintRule> builtInRules = BuiltInRuleRegistry.defaultRules();
        List<LintRule> discoveredRules = ExternalRuleDiscovery.discover(classLoader, java.util.Set.copyOf(BuiltInRuleRegistry.defaultRuleIds()));
        if (discoveredRules.isEmpty()) {
            return builtInRules;
        }
        List<LintRule> allRules = new ArrayList<>(builtInRules.size() + discoveredRules.size());
        allRules.addAll(builtInRules);
        allRules.addAll(discoveredRules);
        return List.copyOf(allRules);
    }
}
