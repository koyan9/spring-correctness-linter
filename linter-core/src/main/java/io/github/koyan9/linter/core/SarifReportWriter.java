/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

final class SarifReportWriter {

    String write(LintReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"$schema\": \"https://json.schemastore.org/sarif-2.1.0.json\",\n");
        builder.append("  \"version\": \"2.1.0\",\n");
        builder.append("  \"runs\": [\n");
        builder.append("    {\n");
        builder.append("      \"tool\": {\n");
        builder.append("        \"driver\": {\n");
        builder.append("          \"name\": \"spring-correctness-linter\",\n");
        builder.append("          \"rules\": [\n");
        for (int index = 0; index < report.rules().size(); index++) {
            RuleDescriptor rule = report.rules().get(index);
            builder.append("            {\n");
            builder.append("              \"id\": \"").append(ReportWriterSupport.escapeJson(rule.id())).append("\",\n");
            builder.append("              \"name\": \"").append(ReportWriterSupport.escapeJson(rule.title())).append("\",\n");
            builder.append("              \"shortDescription\": { \"text\": \"").append(ReportWriterSupport.escapeJson(rule.title())).append("\" },\n");
            builder.append("              \"fullDescription\": { \"text\": \"").append(ReportWriterSupport.escapeJson(rule.description())).append("\" },\n");
            builder.append("              \"defaultConfiguration\": { \"level\": \"").append(ReportWriterSupport.toSarifLevel(rule.defaultSeverity())).append("\" }\n");
            builder.append("            }");
            if (index < report.rules().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("          ]\n");
        builder.append("        }\n");
        builder.append("      },\n");
        builder.append("      \"results\": [\n");
        for (int index = 0; index < report.issues().size(); index++) {
            LintIssue issue = report.issues().get(index);
            builder.append("        {\n");
            builder.append("          \"ruleId\": \"").append(ReportWriterSupport.escapeJson(issue.ruleId())).append("\",\n");
            builder.append("          \"level\": \"").append(ReportWriterSupport.toSarifLevel(issue.severity())).append("\",\n");
            builder.append("          \"message\": { \"text\": \"").append(ReportWriterSupport.escapeJson(issue.message())).append("\" },\n");
            builder.append("          \"properties\": { \"moduleId\": \"")
                    .append(ReportWriterSupport.escapeJson(report.moduleFor(issue.file())))
                    .append("\" },\n");
            builder.append("          \"locations\": [\n");
            builder.append("            {\n");
            builder.append("              \"physicalLocation\": {\n");
            builder.append("                \"artifactLocation\": { \"uri\": \"").append(ReportWriterSupport.escapeJson(ReportWriterSupport.relativePath(report.projectRoot(), issue.file()))).append("\" },\n");
            builder.append("                \"region\": { \"startLine\": ").append(issue.line()).append(" }\n");
            builder.append("              }\n");
            builder.append("            }\n");
            builder.append("          ]\n");
            builder.append("        }");
            if (index < report.issues().size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("      ]\n");
        builder.append("    }\n");
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }
}
