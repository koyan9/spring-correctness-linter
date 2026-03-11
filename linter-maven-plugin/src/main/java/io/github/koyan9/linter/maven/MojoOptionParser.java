/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.LintSeverity;
import org.apache.maven.plugin.MojoExecutionException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MojoOptionParser {

    Set<String> parseRuleIds(String value) {
        Set<String> ruleIds = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return ruleIds;
        }

        for (String token : value.split("[,;]")) {
            if (!token.isBlank()) {
                ruleIds.add(token.trim());
            }
        }
        return ruleIds;
    }

    Set<RuleDomain> parseRuleDomains(String value) throws MojoExecutionException {
        Set<RuleDomain> domains = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return domains;
        }

        for (String token : value.split("[,;]")) {
            if (token.isBlank()) {
                continue;
            }
            domains.add(parseRuleDomain(token.trim()));
        }
        return domains;
    }

    Map<String, LintSeverity> parseSeverityOverrides(String value) throws MojoExecutionException {
        Map<String, LintSeverity> overrides = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return overrides;
        }

        for (String token : value.split("[,;]")) {
            if (token.isBlank()) {
                continue;
            }

            String[] parts = token.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new MojoExecutionException(
                        "Invalid severityOverrides entry: " + token + ". Expected RULE_ID=INFO|WARNING|ERROR."
                );
            }
            overrides.put(parts[0].trim(), parseSeverity(parts[1].trim()));
        }
        return overrides;
    }

    LintSeverity parseSeverity(String value) throws MojoExecutionException {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LintSeverity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new MojoExecutionException("Invalid failOnSeverity value: " + value + ". Expected INFO, WARNING, or ERROR.", exception);
        }
    }

    private RuleDomain parseRuleDomain(String value) throws MojoExecutionException {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return RuleDomain.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            String availableDomains = java.util.Arrays.stream(RuleDomain.values())
                    .map(Enum::name)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            throw new MojoExecutionException("Invalid rule domain value: " + value + ". Expected one of: " + availableDomains + ".", exception);
        }
    }
}
