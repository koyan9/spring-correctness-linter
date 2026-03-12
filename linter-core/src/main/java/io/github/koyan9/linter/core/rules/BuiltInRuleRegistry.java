/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.RuleDomain;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

final class BuiltInRuleRegistry {

    private static final List<RuleRegistration> DEFAULT_RULES = List.of(
            new RuleRegistration("SPRING_ASYNC_VOID", RuleDomain.ASYNC, AsyncVoidMethodRule::new),
            new RuleRegistration("SPRING_ASYNC_PRIVATE_METHOD", RuleDomain.ASYNC, AsyncPrivateMethodRule::new),
            new RuleRegistration("SPRING_ASYNC_SELF_INVOCATION", RuleDomain.ASYNC, AsyncSelfInvocationRule::new),
            new RuleRegistration("SPRING_ASYNC_TRANSACTIONAL_BOUNDARY", RuleDomain.ASYNC, AsyncTransactionalBoundaryRule::new),
            new RuleRegistration("SPRING_LIFECYCLE_ASYNC_BOUNDARY", RuleDomain.LIFECYCLE, LifecycleAsyncBoundaryRule::new),
            new RuleRegistration("SPRING_LIFECYCLE_TRANSACTIONAL_BOUNDARY", RuleDomain.LIFECYCLE, LifecycleTransactionalBoundaryRule::new),
            new RuleRegistration("SPRING_STARTUP_ASYNC_BOUNDARY", RuleDomain.LIFECYCLE, StartupAsyncBoundaryRule::new),
            new RuleRegistration("SPRING_STARTUP_TRANSACTIONAL_BOUNDARY", RuleDomain.LIFECYCLE, StartupTransactionalBoundaryRule::new),
            new RuleRegistration("SPRING_SCHEDULED_METHOD_PARAMETERS", RuleDomain.SCHEDULED, ScheduledMethodArgumentsRule::new),
            new RuleRegistration("SPRING_SCHEDULED_RETURN_VALUE", RuleDomain.SCHEDULED, ScheduledReturnValueRule::new),
            new RuleRegistration("SPRING_SCHEDULED_TRIGGER_CONFIGURATION", RuleDomain.SCHEDULED, ScheduledTriggerConfigurationRule::new),
            new RuleRegistration("SPRING_SCHEDULED_REPEATED_TRIGGER", RuleDomain.SCHEDULED, ScheduledRepeatedTriggerRule::new),
            new RuleRegistration("SPRING_SCHEDULED_NON_POSITIVE_INTERVAL", RuleDomain.SCHEDULED, ScheduledNonPositiveIntervalRule::new),
            new RuleRegistration("SPRING_SCHEDULED_ASYNC_BOUNDARY", RuleDomain.SCHEDULED, ScheduledAsyncBoundaryRule::new),
            new RuleRegistration("SPRING_SCHEDULED_TRANSACTIONAL_BOUNDARY", RuleDomain.SCHEDULED, ScheduledTransactionalBoundaryRule::new),
            new RuleRegistration("SPRING_CACHEABLE_KEY", RuleDomain.CACHE, CacheableWithoutKeyRule::new),
            new RuleRegistration("SPRING_CACHE_COMBINATION_RISK", RuleDomain.CACHE, CacheAnnotationCombinationRiskRule::new),
            new RuleRegistration("SPRING_PROFILE_CONTROLLER", RuleDomain.WEB, ProfileOnControllerRule::new),
            new RuleRegistration("SPRING_TX_SELF_INVOCATION", RuleDomain.TRANSACTION, TransactionalSelfInvocationRule::new),
            new RuleRegistration("SPRING_TX_PRIVATE_METHOD", RuleDomain.TRANSACTION, TransactionalPrivateMethodRule::new),
            new RuleRegistration("SPRING_TX_FINAL_METHOD", RuleDomain.TRANSACTION, TransactionalFinalMethodRule::new),
            new RuleRegistration("SPRING_EVENT_LISTENER_TRANSACTIONAL", RuleDomain.EVENTS, EventListenerTransactionalRule::new),
            new RuleRegistration("SPRING_TRANSACTIONAL_EVENT_LISTENER", RuleDomain.EVENTS, TransactionalEventListenerRule::new),
            new RuleRegistration("SPRING_TX_HIGH_RISK_PROPAGATION", RuleDomain.TRANSACTION, TransactionalHighRiskPropagationRule::new),
            new RuleRegistration("SPRING_CONDITIONAL_BEAN_CONFLICT", RuleDomain.CONFIGURATION, ConditionalOnBeanConflictRule::new),
            new RuleRegistration("SPRING_ENDPOINT_SECURITY", RuleDomain.WEB, PublicEndpointWithoutSecurityRule::new)
    );

    static {
        validateUniqueIds(DEFAULT_RULES);
    }

    private BuiltInRuleRegistry() {
    }

    static List<LintRule> defaultRules() {
        return DEFAULT_RULES.stream()
                .map(RuleRegistration::newRule)
                .toList();
    }

    static List<String> defaultRuleIds() {
        return DEFAULT_RULES.stream()
                .map(RuleRegistration::id)
                .toList();
    }

    static List<BuiltInRuleGroup> defaultRuleGroups() {
        Map<RuleDomain, List<String>> byDomain = new LinkedHashMap<>();
        for (RuleRegistration registration : DEFAULT_RULES) {
            byDomain.computeIfAbsent(registration.domain(), ignored -> new java.util.ArrayList<>())
                    .add(registration.id());
        }
        return byDomain.entrySet().stream()
                .map(entry -> new BuiltInRuleGroup(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static void validateUniqueIds(List<RuleRegistration> registrations) {
        Set<String> seen = new HashSet<>();
        for (RuleRegistration registration : registrations) {
            if (!seen.add(registration.id())) {
                throw new IllegalStateException("Duplicate built-in rule id: " + registration.id());
            }
        }
    }

    private record RuleRegistration(String id, RuleDomain domain, Supplier<LintRule> factory) {

        private LintRule newRule() {
            LintRule rule = factory.get();
            if (!rule.id().equals(id)) {
                throw new IllegalStateException("Built-in rule id mismatch. Registered=" + id + ", actual=" + rule.id());
            }
            if (rule.domain() != domain) {
                throw new IllegalStateException("Built-in rule domain mismatch. Registered=" + domain + ", actual=" + rule.domain() + ", rule=" + rule.id());
            }
            return rule;
        }
    }
}
