/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleSelectionTest {

    @Test
    void filtersRulesAndOverridesSeverity() throws Exception {
        LintRule alphaRule = new TestRule("RULE_ALPHA", RuleDomain.ASYNC, LintSeverity.WARNING);
        LintRule betaRule = new TestRule("RULE_BETA", LintSeverity.WARNING);
        List<LintRule> configuredRules = RuleSelection.configure(
                List.of(alphaRule, betaRule),
                Set.of("RULE_ALPHA"),
                Set.of(),
                Map.of("RULE_ALPHA", LintSeverity.ERROR)
        );

        assertEquals(1, configuredRules.size());
        assertEquals("RULE_ALPHA", configuredRules.get(0).id());
        assertEquals(LintSeverity.ERROR, configuredRules.get(0).severity());

        List<LintIssue> issues = configuredRules.get(0).evaluate(
                new SourceUnit(Path.of("Demo.java"), "class Demo {}"),
                ProjectContext.load(Path.of("."), Path.of("missing-src"))
        );
        assertEquals(1, issues.size());
        assertEquals(LintSeverity.ERROR, issues.get(0).severity());
        assertEquals(RuleDomain.ASYNC, configuredRules.get(0).domain());
    }

    @Test
    void filtersRulesByEnabledDomainsAndDisabledRules() {
        List<LintRule> configuredRules = RuleSelection.configure(
                List.of(
                        new TestRule("RULE_ASYNC", RuleDomain.ASYNC, LintSeverity.WARNING),
                        new TestRule("RULE_TX", RuleDomain.TRANSACTION, LintSeverity.WARNING),
                        new TestRule("RULE_TX_2", RuleDomain.TRANSACTION, LintSeverity.ERROR)
                ),
                Set.of(),
                Set.of("RULE_TX_2"),
                Set.of(RuleDomain.TRANSACTION),
                Set.of(),
                Map.of()
        );

        assertEquals(List.of("RULE_TX"), configuredRules.stream().map(LintRule::id).toList());
    }

    @Test
    void rejectsOverlappingEnabledAndDisabledDomains() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> RuleSelection.configure(
                        List.of(new TestRule("RULE_ALPHA", RuleDomain.ASYNC, LintSeverity.WARNING)),
                        Set.of(),
                        Set.of(),
                        Set.of(RuleDomain.ASYNC),
                        Set.of(RuleDomain.ASYNC),
                        Map.of()
                )
        );

        assertEquals(true, exception.getMessage().contains("ASYNC"));
    }

    @Test
    void rejectsUnknownRuleIds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> RuleSelection.configure(
                        List.of(new TestRule("RULE_ALPHA", LintSeverity.WARNING)),
                        Set.of("RULE_UNKNOWN"),
                        Set.of(),
                        Map.of()
                )
        );

        assertEquals(true, exception.getMessage().contains("RULE_UNKNOWN"));
    }

    private static final class TestRule implements LintRule {

        private final String id;
        private final RuleDomain domain;
        private final LintSeverity severity;

        private TestRule(String id, LintSeverity severity) {
            this(id, RuleDomain.GENERAL, severity);
        }

        private TestRule(String id, RuleDomain domain, LintSeverity severity) {
            this.id = id;
            this.domain = domain;
            this.severity = severity;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String title() {
            return id;
        }

        @Override
        public String description() {
            return id + " description";
        }

        @Override
        public RuleDomain domain() {
            return domain;
        }

        @Override
        public LintSeverity severity() {
            return severity;
        }

        @Override
        public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
            return List.of(new LintIssue(id, severity, id + " issue", sourceUnit.path(), 1));
        }
    }
}
