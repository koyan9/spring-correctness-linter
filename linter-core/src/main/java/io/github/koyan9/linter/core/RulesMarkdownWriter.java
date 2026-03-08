/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

final class RulesMarkdownWriter {

    String write(Iterable<RuleDescriptor> rules) {
        java.util.List<RuleDescriptor> descriptors = new java.util.ArrayList<>();
        for (RuleDescriptor rule : rules) {
            descriptors.add(rule);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# Generated Rule Reference\n\n");
        builder.append("This file is generated from the current rule registry. Use it as the default rule index for CLI, Maven, and CI integration.\n\n");
        builder.append("## Usage\n\n");
        builder.append("- Disable rules: `-Dspring.correctness.linter.disabledRules=RULE_A,RULE_B`\n");
        builder.append("- Enable only selected rules: `-Dspring.correctness.linter.enabledRules=RULE_A,RULE_B`\n");
        builder.append("- Override severities: `-Dspring.correctness.linter.severityOverrides=RULE_A=ERROR,RULE_B=INFO`\n");
        builder.append("- Suppress one finding: `// spring-correctness-linter:disable-next-line RULE_ID reason: explanation`\n\n");
        builder.append("## Rule Index\n\n");
        builder.append("| Rule ID | Default Severity | Title |\n");
        builder.append("| --- | --- | --- |\n");
        for (RuleDescriptor rule : descriptors) {
            builder.append("| `").append(rule.id()).append("` | `")
                    .append(rule.defaultSeverity()).append("` | ")
                    .append(rule.title()).append(" |\n");
        }
        builder.append("\n## Rule Details\n\n");
        for (RuleDescriptor rule : descriptors) {
            builder.append("### `").append(rule.id()).append("`\n");
            builder.append("- Title: ").append(rule.title()).append("\n");
            builder.append("- Default severity: `").append(rule.defaultSeverity()).append("`\n");
            builder.append("- Description: ").append(rule.description()).append("\n");
            builder.append("- Disable: `-Dspring.correctness.linter.disabledRules=").append(rule.id()).append("`\n");
            builder.append("- Run only this rule: `-Dspring.correctness.linter.enabledRules=").append(rule.id()).append("`\n");
            builder.append("- Override severity: `-Dspring.correctness.linter.severityOverrides=").append(rule.id()).append("=ERROR`\n");
            builder.append("- Suppress next line: `// spring-correctness-linter:disable-next-line ").append(rule.id()).append(" reason: explain why`\n");
            builder.append("- Suppress next method: `// spring-correctness-linter:disable-next-method ").append(rule.id()).append(" reason: explain why`\n\n");
        }
        return builder.toString();
    }
}
