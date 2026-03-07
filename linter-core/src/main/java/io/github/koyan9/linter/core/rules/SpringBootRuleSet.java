/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import io.github.koyan9.linter.core.LintRule;

import java.util.List;

public final class SpringBootRuleSet {

    private SpringBootRuleSet() {
    }

    public static List<LintRule> defaultRules() {
        return List.of(
                new AsyncVoidMethodRule(),
                new AsyncPrivateMethodRule(),
                new CacheableWithoutKeyRule(),
                new CacheAnnotationCombinationRiskRule(),
                new ProfileOnControllerRule(),
                new TransactionalSelfInvocationRule(),
                new TransactionalPrivateMethodRule(),
                new TransactionalFinalMethodRule(),
                new EventListenerTransactionalRule(),
                new TransactionalEventListenerRule(),
                new TransactionalHighRiskPropagationRule(),
                new ConditionalOnBeanConflictRule(),
                new PublicEndpointWithoutSecurityRule()
        );
    }
}