/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InlineSuppressions {

    private static final int NEXT_LINE_WINDOW = 3;
    private static final Pattern DIRECTIVE_PATTERN = Pattern.compile(
            "medical-linter:(?<scope>disable-file|disable-line|disable-next-line|disable-next-method|disable-next-type)\\s+(?<rules>[A-Z0-9_,*\\s-]+?)(?:\\s+reason\\s*[:=]\\s*(?<reason>.+))?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final InlineSuppressions NONE = new InlineSuppressions(List.of(), Map.of(), List.of());

    private final List<SuppressionDirective> fileDirectives;
    private final Map<Integer, List<SuppressionDirective>> lineDirectives;
    private final List<LineRangeDirective> rangeDirectives;

    private InlineSuppressions(
            List<SuppressionDirective> fileDirectives,
            Map<Integer, List<SuppressionDirective>> lineDirectives,
            List<LineRangeDirective> rangeDirectives
    ) {
        this.fileDirectives = List.copyOf(fileDirectives);
        this.lineDirectives = Map.copyOf(lineDirectives);
        this.rangeDirectives = List.copyOf(rangeDirectives);
    }

    public static InlineSuppressions none() {
        return NONE;
    }

    public static InlineSuppressions parse(SourceUnit sourceUnit) {
        String content = sourceUnit.content();
        List<SuppressionDirective> fileDirectives = new ArrayList<>();
        Map<Integer, List<SuppressionDirective>> lineDirectives = new HashMap<>();
        List<LineRangeDirective> rangeDirectives = new ArrayList<>();

        String[] lines = content.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            Matcher matcher = DIRECTIVE_PATTERN.matcher(lines[index]);
            if (!matcher.find()) {
                continue;
            }

            String scope = matcher.group("scope").toLowerCase();
            SuppressionDirective directive = new SuppressionDirective(
                    parseRules(matcher.group("rules")),
                    normalizeReason(matcher.group("reason"))
            );
            int lineNumber = index + 1;
            switch (scope) {
                case "disable-file" -> fileDirectives.add(directive);
                case "disable-line" -> merge(lineDirectives, lineNumber, directive);
                case "disable-next-line" -> mergeWindow(lineDirectives, lineNumber + 1, NEXT_LINE_WINDOW, directive);
                case "disable-next-method" -> resolveNextMethodRange(sourceUnit, lineNumber).ifPresent(range -> rangeDirectives.add(new LineRangeDirective(range.startLine(), range.endLine(), directive)));
                case "disable-next-type" -> resolveNextTypeRange(sourceUnit, lineNumber).ifPresent(range -> rangeDirectives.add(new LineRangeDirective(range.startLine(), range.endLine(), directive)));
                default -> {
                }
            }
        }

        if (fileDirectives.isEmpty() && lineDirectives.isEmpty() && rangeDirectives.isEmpty()) {
            return NONE;
        }
        return new InlineSuppressions(fileDirectives, lineDirectives, rangeDirectives);
    }

    public boolean suppresses(LintIssue issue) {
        return findDirective(issue).isPresent();
    }

    public Optional<String> reasonFor(LintIssue issue) {
        return findDirective(issue)
                .map(SuppressionDirective::reason)
                .filter(reason -> reason != null && !reason.isBlank());
    }

    private Optional<SuppressionDirective> findDirective(LintIssue issue) {
        List<SuppressionDirective> lineMatches = lineDirectives.get(issue.line());
        if (lineMatches != null) {
            for (SuppressionDirective directive : lineMatches) {
                if (matches(directive.rules(), issue.ruleId())) {
                    return Optional.of(directive);
                }
            }
        }

        for (LineRangeDirective rangeDirective : rangeDirectives) {
            if (rangeDirective.contains(issue.line()) && matches(rangeDirective.directive().rules(), issue.ruleId())) {
                return Optional.of(rangeDirective.directive());
            }
        }

        for (SuppressionDirective directive : fileDirectives) {
            if (matches(directive.rules(), issue.ruleId())) {
                return Optional.of(directive);
            }
        }
        return Optional.empty();
    }

    private static Optional<LineRange> resolveNextMethodRange(SourceUnit sourceUnit, int afterLine) {
        return sourceUnit.compilationUnit()
                .flatMap(compilationUnit -> compilationUnit.findAll(MethodDeclaration.class).stream()
                        .filter(method -> JavaSourceInspector.lineOf(method) > afterLine)
                        .sorted(java.util.Comparator.comparingInt(JavaSourceInspector::lineOf))
                        .findFirst()
                        .map(InlineSuppressions::toRange));
    }

    private static Optional<LineRange> resolveNextTypeRange(SourceUnit sourceUnit, int afterLine) {
        return sourceUnit.compilationUnit()
                .flatMap(compilationUnit -> JavaSourceInspector.findTypeDeclarations(compilationUnit).stream()
                        .filter(typeDeclaration -> JavaSourceInspector.lineOf(typeDeclaration) > afterLine)
                        .sorted(java.util.Comparator.comparingInt(JavaSourceInspector::lineOf))
                        .findFirst()
                        .map(InlineSuppressions::toRange));
    }

    private static LineRange toRange(MethodDeclaration methodDeclaration) {
        int startLine = JavaSourceInspector.lineOf(methodDeclaration);
        int endLine = methodDeclaration.getEnd().map(position -> position.line).orElse(startLine);
        return new LineRange(startLine, endLine);
    }

    private static LineRange toRange(TypeDeclaration<?> typeDeclaration) {
        int startLine = JavaSourceInspector.lineOf(typeDeclaration);
        int endLine = typeDeclaration.getEnd().map(position -> position.line).orElse(startLine);
        return new LineRange(startLine, endLine);
    }

    private static void merge(Map<Integer, List<SuppressionDirective>> lineDirectives, int lineNumber, SuppressionDirective directive) {
        lineDirectives.computeIfAbsent(lineNumber, ignored -> new ArrayList<>()).add(directive);
    }

    private static void mergeWindow(Map<Integer, List<SuppressionDirective>> lineDirectives, int firstLine, int windowSize, SuppressionDirective directive) {
        for (int offset = 0; offset < windowSize; offset++) {
            merge(lineDirectives, firstLine + offset, directive);
        }
    }

    private static Set<String> parseRules(String raw) {
        return Arrays.stream(raw.trim().toUpperCase().split("[\\s,]+"))
                .filter(token -> !token.isBlank())
                .collect(HashSet::new, Set::add, Set::addAll);
    }

    private static String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean matches(Set<String> rules, String ruleId) {
        return rules != null && (rules.contains("*") || rules.contains(ruleId.toUpperCase()));
    }

    private record LineRange(int startLine, int endLine) {
    }

    private record LineRangeDirective(int startLine, int endLine, SuppressionDirective directive) {

        private boolean contains(int line) {
            return line >= startLine && line <= endLine;
        }
    }

    private record SuppressionDirective(Set<String> rules, String reason) {
    }
}