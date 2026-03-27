/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.LintSeverity;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.spi.LintRuleProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringBootRuleSetExtensionTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversExternalLintRuleProviders() throws Exception {
        ClassLoader classLoader = classLoaderForProviders(ExternalDemoRuleProvider.class);

        List<LintRule> rules = SpringBootRuleSet.defaultRules(classLoader);

        assertTrue(rules.stream().anyMatch(rule -> rule.id().equals("EXTERNAL_DEMO_RULE")));
    }

    @Test
    void rejectsDuplicateRuleIdsFromExternalProviders() throws Exception {
        ClassLoader classLoader = classLoaderForProviders(DuplicateBuiltInRuleProvider.class);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> SpringBootRuleSet.defaultRules(classLoader));

        assertTrue(exception.getMessage().contains("Duplicate rule id"));
        assertTrue(exception.getMessage().contains("SPRING_ASYNC_VOID"));
    }

    private ClassLoader classLoaderForProviders(Class<?>... providerClasses) throws Exception {
        Path servicesDirectory = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDirectory);
        Files.writeString(
                servicesDirectory.resolve("io.github.koyan9.linter.core.spi.LintRuleProvider"),
                String.join(System.lineSeparator(), java.util.Arrays.stream(providerClasses).map(Class::getName).toList())
        );
        URL[] urls = { tempDir.toUri().toURL() };
        return new URLClassLoader(urls, getClass().getClassLoader());
    }

    public static final class ExternalDemoRuleProvider implements LintRuleProvider {

        @Override
        public List<LintRule> rules() {
            return List.of(new ExternalDemoRule());
        }
    }

    public static final class DuplicateBuiltInRuleProvider implements LintRuleProvider {

        @Override
        public List<LintRule> rules() {
            return List.of(new DuplicateBuiltInRule());
        }
    }

    public static final class ExternalDemoRule implements LintRule {

        @Override
        public String id() {
            return "EXTERNAL_DEMO_RULE";
        }

        @Override
        public String title() {
            return "External demo rule";
        }

        @Override
        public String description() {
            return "Verifies that external lint rules can be discovered.";
        }

        @Override
        public LintSeverity severity() {
            return LintSeverity.INFO;
        }

        @Override
        public RuleDomain domain() {
            return RuleDomain.GENERAL;
        }

        @Override
        public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
            return List.of();
        }
    }

    public static final class DuplicateBuiltInRule implements LintRule {

        @Override
        public String id() {
            return "SPRING_ASYNC_VOID";
        }

        @Override
        public String title() {
            return "Duplicate built-in rule";
        }

        @Override
        public String description() {
            return "Used only for duplicate-id discovery tests.";
        }

        @Override
        public LintSeverity severity() {
            return LintSeverity.WARNING;
        }

        @Override
        public RuleDomain domain() {
            return RuleDomain.GENERAL;
        }

        @Override
        public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
            return List.of();
        }
    }
}
