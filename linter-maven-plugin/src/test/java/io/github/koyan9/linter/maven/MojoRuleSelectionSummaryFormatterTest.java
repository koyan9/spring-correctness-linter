/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.RuleDomainRuleSummary;
import io.github.koyan9.linter.core.RuleDomainSelectionSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MojoRuleSelectionSummaryFormatterTest {

    @Test
    void formatsConfiguredAndEffectiveSelection() {
        String formatted = new MojoRuleSelectionSummaryFormatter().format(new RuleDomainSelectionSummary(
                List.of(RuleDomain.TRANSACTION),
                List.of(RuleDomain.WEB),
                List.of(RuleDomain.TRANSACTION),
                List.of("SPRING_TX_PRIVATE_METHOD"),
                List.of("SPRING_ENDPOINT_SECURITY"),
                List.of("SPRING_TX_FINAL_METHOD", "SPRING_TX_PRIVATE_METHOD"),
                List.of(new RuleDomainRuleSummary(RuleDomain.TRANSACTION, List.of("SPRING_TX_FINAL_METHOD", "SPRING_TX_PRIVATE_METHOD")))
        ));

        assertEquals(
                "spring-correctness-linter selection: enabled domains=TRANSACTION; disabled domains=WEB; enabled rules=[SPRING_TX_PRIVATE_METHOD]; disabled rules=[SPRING_ENDPOINT_SECURITY]; effective domains=TRANSACTION; effective rules=2 [SPRING_TX_FINAL_METHOD,SPRING_TX_PRIVATE_METHOD]",
                formatted
        );
    }

    @Test
    void truncatesLongRuleLists() {
        String formatted = new MojoRuleSelectionSummaryFormatter().format(new RuleDomainSelectionSummary(
                List.of(),
                List.of(),
                List.of(RuleDomain.ASYNC),
                List.of(),
                List.of(),
                List.of("RULE_A", "RULE_B", "RULE_C", "RULE_D", "RULE_E", "RULE_F"),
                List.of(new RuleDomainRuleSummary(RuleDomain.ASYNC, List.of("RULE_A", "RULE_B", "RULE_C", "RULE_D", "RULE_E", "RULE_F")))
        ));

        assertEquals(
                "spring-correctness-linter selection: effective domains=ASYNC; effective rules=6 [RULE_A,RULE_B,RULE_C,RULE_D,RULE_E,...]",
                formatted
        );
    }
}
