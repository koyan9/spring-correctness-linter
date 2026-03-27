/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.RuleDomain;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class BuiltInRuleRegistryTest {

    @Test
    void exposesStableDefaultRuleOrder() {
        assertEquals(List.of(
                "SPRING_ASYNC_VOID",
                "SPRING_ASYNC_UNSUPPORTED_RETURN_TYPE",
                "SPRING_ASYNC_PRIVATE_METHOD",
                "SPRING_ASYNC_FINAL_METHOD",
                "SPRING_ASYNC_FINAL_CLASS",
                "SPRING_ASYNC_SELF_INVOCATION",
                "SPRING_ASYNC_TRANSACTIONAL_BOUNDARY",
                "SPRING_LIFECYCLE_ASYNC_BOUNDARY",
                "SPRING_LIFECYCLE_TRANSACTIONAL_BOUNDARY",
                "SPRING_STARTUP_ASYNC_BOUNDARY",
                "SPRING_STARTUP_TRANSACTIONAL_BOUNDARY",
                "SPRING_SCHEDULED_METHOD_PARAMETERS",
                "SPRING_SCHEDULED_RETURN_VALUE",
                "SPRING_SCHEDULED_TRIGGER_CONFIGURATION",
                "SPRING_SCHEDULED_REPEATED_TRIGGER",
                "SPRING_SCHEDULED_NON_POSITIVE_INTERVAL",
                "SPRING_SCHEDULED_ASYNC_BOUNDARY",
                "SPRING_SCHEDULED_TRANSACTIONAL_BOUNDARY",
                "SPRING_CACHEABLE_KEY",
                "SPRING_CACHEABLE_PRIVATE_METHOD",
                "SPRING_CACHEABLE_FINAL_METHOD",
                "SPRING_CACHEABLE_SELF_INVOCATION",
                "SPRING_CACHE_COMBINATION_RISK",
                "SPRING_PROFILE_CONTROLLER",
                "SPRING_TX_SELF_INVOCATION",
                "SPRING_TX_PRIVATE_METHOD",
                "SPRING_TX_FINAL_METHOD",
                "SPRING_TX_FINAL_CLASS",
                "SPRING_EVENT_LISTENER_TRANSACTIONAL",
                "SPRING_TRANSACTIONAL_EVENT_LISTENER",
                "SPRING_TX_HIGH_RISK_PROPAGATION",
                "SPRING_CONDITIONAL_BEAN_CONFLICT",
                "SPRING_ENDPOINT_SECURITY"
        ), BuiltInRuleRegistry.defaultRuleIds());
    }

    @Test
    void createsFreshRuleInstancesWithUniqueIds() {
        List<LintRule> first = BuiltInRuleRegistry.defaultRules();
        List<LintRule> second = BuiltInRuleRegistry.defaultRules();

        assertEquals(BuiltInRuleRegistry.defaultRuleIds(), first.stream().map(LintRule::id).toList());
        assertEquals(first.size(), Set.copyOf(first.stream().map(LintRule::id).toList()).size());
        assertEquals(first.size(), second.size());
        assertNotSame(first.get(0), second.get(0));
    }

    @Test
    void exposesStableRuleGroups() {
        assertEquals(List.of(
                new BuiltInRuleGroup(RuleDomain.ASYNC, List.of("SPRING_ASYNC_VOID", "SPRING_ASYNC_UNSUPPORTED_RETURN_TYPE", "SPRING_ASYNC_PRIVATE_METHOD", "SPRING_ASYNC_FINAL_METHOD", "SPRING_ASYNC_FINAL_CLASS", "SPRING_ASYNC_SELF_INVOCATION", "SPRING_ASYNC_TRANSACTIONAL_BOUNDARY")),
                new BuiltInRuleGroup(RuleDomain.LIFECYCLE, List.of("SPRING_LIFECYCLE_ASYNC_BOUNDARY", "SPRING_LIFECYCLE_TRANSACTIONAL_BOUNDARY", "SPRING_STARTUP_ASYNC_BOUNDARY", "SPRING_STARTUP_TRANSACTIONAL_BOUNDARY")),
                new BuiltInRuleGroup(RuleDomain.SCHEDULED, List.of("SPRING_SCHEDULED_METHOD_PARAMETERS", "SPRING_SCHEDULED_RETURN_VALUE", "SPRING_SCHEDULED_TRIGGER_CONFIGURATION", "SPRING_SCHEDULED_REPEATED_TRIGGER", "SPRING_SCHEDULED_NON_POSITIVE_INTERVAL", "SPRING_SCHEDULED_ASYNC_BOUNDARY", "SPRING_SCHEDULED_TRANSACTIONAL_BOUNDARY")),
                new BuiltInRuleGroup(RuleDomain.CACHE, List.of("SPRING_CACHEABLE_KEY", "SPRING_CACHEABLE_PRIVATE_METHOD", "SPRING_CACHEABLE_FINAL_METHOD", "SPRING_CACHEABLE_SELF_INVOCATION", "SPRING_CACHE_COMBINATION_RISK")),
                new BuiltInRuleGroup(RuleDomain.WEB, List.of("SPRING_PROFILE_CONTROLLER", "SPRING_ENDPOINT_SECURITY")),
                new BuiltInRuleGroup(RuleDomain.TRANSACTION, List.of("SPRING_TX_SELF_INVOCATION", "SPRING_TX_PRIVATE_METHOD", "SPRING_TX_FINAL_METHOD", "SPRING_TX_FINAL_CLASS", "SPRING_TX_HIGH_RISK_PROPAGATION")),
                new BuiltInRuleGroup(RuleDomain.EVENTS, List.of("SPRING_EVENT_LISTENER_TRANSACTIONAL", "SPRING_TRANSACTIONAL_EVENT_LISTENER")),
                new BuiltInRuleGroup(RuleDomain.CONFIGURATION, List.of("SPRING_CONDITIONAL_BEAN_CONFLICT"))
        ), BuiltInRuleRegistry.defaultRuleGroups());
    }
}
