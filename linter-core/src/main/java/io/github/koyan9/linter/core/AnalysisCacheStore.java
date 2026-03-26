/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnalysisCacheStore {

    public static final String CACHE_REASON_CACHE_NOT_CONFIGURED = "cache-not-configured";
    public static final String CACHE_REASON_CACHE_FILES_MISSING = "cache-files-missing";
    public static final String CACHE_REASON_CACHE_METADATA_UNAVAILABLE = "cache-metadata-unavailable";
    public static final String CACHE_REASON_RULE_OR_CONFIG_CHANGED = "rule-or-config-changed";
    public static final String CACHE_REASON_SOURCE_ROOTS_CHANGED = "source-roots-changed";
    public static final String CACHE_REASON_ANNOTATION_OR_TYPE_CONTEXT_CHANGED = "annotation-or-type-context-changed";
    public static final String CACHE_REASON_AUTO_DETECT_CONTEXT_CHANGED = "auto-detect-context-changed";
    public static final String CACHE_REASON_MODIFIED_OR_NEW_FILES = "modified-or-new-files";

    private static final String HEADER = "# spring-correctness-linter analysis cache v4";
    private static final List<String> FINGERPRINT_COMPONENT_ORDER = List.of(
            CACHE_REASON_RULE_OR_CONFIG_CHANGED,
            CACHE_REASON_SOURCE_ROOTS_CHANGED,
            CACHE_REASON_ANNOTATION_OR_TYPE_CONTEXT_CHANGED,
            CACHE_REASON_AUTO_DETECT_CONTEXT_CHANGED
    );

    public static String describeCacheMissReason(String reason) {
        return switch (reason) {
            case CACHE_REASON_CACHE_NOT_CONFIGURED ->
                    "cache is enabled but no reusable cache location was configured before analysis started";
            case CACHE_REASON_CACHE_FILES_MISSING ->
                    "no reusable cache files were available before analysis started";
            case CACHE_REASON_CACHE_METADATA_UNAVAILABLE ->
                    "existing cache metadata could not be compared safely";
            case CACHE_REASON_RULE_OR_CONFIG_CHANGED ->
                    "rule or semantic configuration changed";
            case CACHE_REASON_SOURCE_ROOTS_CHANGED ->
                    "source-root composition changed";
            case CACHE_REASON_ANNOTATION_OR_TYPE_CONTEXT_CHANGED ->
                    "annotation or type context changed";
            case CACHE_REASON_AUTO_DETECT_CONTEXT_CHANGED ->
                    "auto-detect context changed";
            case CACHE_REASON_MODIFIED_OR_NEW_FILES ->
                    "all cached files were modified or newly discovered";
            default -> reason;
        };
    }

    public CacheState load(Path cacheFile, CacheFingerprint fingerprint) throws IOException {
        if (cacheFile == null || !Files.exists(cacheFile)) {
            return CacheState.empty(List.of(CACHE_REASON_CACHE_FILES_MISSING));
        }

        Map<String, MutableAnalysis> analyses = new LinkedHashMap<>();
        String cachedFingerprint = null;
        Map<String, String> cachedComponents = new LinkedHashMap<>();
        String pendingLine = null;

        try (java.io.BufferedReader reader = Files.newBufferedReader(cacheFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length == 0) {
                    continue;
                }

                if (isCacheDataRecord(parts[0])) {
                    pendingLine = line;
                    break;
                }

                switch (parts[0]) {
                    case "fingerprint" -> {
                        if (parts.length >= 2) {
                            cachedFingerprint = parts[1];
                        }
                    }
                    case "component" -> {
                        if (parts.length >= 3) {
                            cachedComponents.put(parts[1], parts[2]);
                        }
                    }
                    case "file" -> {
                        if (parts.length >= 4) {
                            String relativePath = decode(parts[1]);
                            String moduleId = parts.length >= 5 ? decode(parts[4]) : ".";
                            analyses.put(relativePath, new MutableAnalysis(relativePath, parts[2], Long.parseLong(parts[3]), moduleId));
                        }
                    }
                    case "parse" -> {
                        if (parts.length >= 3) {
                            String relativePath = decode(parts[1]);
                            analyses.computeIfAbsent(relativePath, key -> new MutableAnalysis(relativePath, "", 0L, "."))
                                    .parseProblemMessages.add(decode(parts[2]));
                        }
                    }
                    case "issue" -> {
                        if (parts.length >= 6) {
                            String relativePath = decode(parts[1]);
                            analyses.computeIfAbsent(relativePath, key -> new MutableAnalysis(relativePath, "", 0L, "."))
                                    .issues.add(new CachedFileAnalysis.CachedIssue(
                                            parts[2],
                                            LintSeverity.valueOf(parts[3]),
                                            Integer.parseInt(parts[4]),
                                            decode(parts[5])
                                    ));
                        }
                    }
                    default -> {
                    }
                }
            }
            if (cachedFingerprint == null || !cachedFingerprint.equals(fingerprint.value())) {
                return CacheState.empty(invalidationReasons(fingerprint, cachedComponents));
            }

            if (pendingLine != null) {
                appendCacheRecord(analyses, pendingLine);
            }
            while ((line = reader.readLine()) != null) {
                appendCacheRecord(analyses, line);
            }
        }

        Map<String, CachedFileAnalysis> results = new LinkedHashMap<>();
        for (MutableAnalysis analysis : analyses.values()) {
            results.put(
                    analysis.relativePath,
                    new CachedFileAnalysis(
                            analysis.moduleId,
                            analysis.relativePath,
                            analysis.contentHash,
                            analysis.issues,
                            analysis.suppressedIssueCount,
                            analysis.parseProblemMessages
                    )
            );
        }
        return new CacheState(results, List.of());
    }

    public void write(Path cacheFile, CacheFingerprint fingerprint, List<CachedFileAnalysis> analyses) throws IOException {
        if (cacheFile == null) {
            return;
        }

        Path parent = cacheFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder builder = new StringBuilder();
        builder.append(HEADER).append(System.lineSeparator());
        builder.append("fingerprint\t").append(fingerprint.value()).append(System.lineSeparator());
        FINGERPRINT_COMPONENT_ORDER.forEach(component -> builder.append("component\t")
                .append(component).append('\t')
                .append(fingerprint.components().getOrDefault(component, ""))
                .append(System.lineSeparator()));
        analyses.stream()
                .sorted(Comparator.comparing(CachedFileAnalysis::relativePath))
                .forEach(analysis -> appendAnalysis(builder, analysis));
        Files.writeString(cacheFile, builder.toString());
    }

    public CacheFingerprint fingerprint(
            List<LintRule> rules,
            LintOptions options,
            ProjectContext context,
            boolean includeTypeResolutionContext,
            boolean includeAutoDetectContext
    ) {
        Map<String, String> components = new LinkedHashMap<>();
        components.put(CACHE_REASON_RULE_OR_CONFIG_CHANGED, hashRuleAndConfig(rules, options));
        components.put(CACHE_REASON_SOURCE_ROOTS_CHANGED, hashEntries("source-roots", context.sourceRootFingerprintEntries()));
        List<String> annotationAndTypeEntries = new ArrayList<>(context.annotationFingerprintEntries());
        if (includeTypeResolutionContext) {
            annotationAndTypeEntries.addAll(context.typeResolutionFingerprintEntries());
        }
        components.put(CACHE_REASON_ANNOTATION_OR_TYPE_CONTEXT_CHANGED, hashEntries("annotation-or-type-context", annotationAndTypeEntries));
        components.put(CACHE_REASON_AUTO_DETECT_CONTEXT_CHANGED, hashEntries(
                "auto-detect-context",
                includeAutoDetectContext ? context.autoDetectFingerprintEntries() : List.of()
        ));
        String overall = hashEntries(
                "overall-fingerprint",
                FINGERPRINT_COMPONENT_ORDER.stream()
                        .map(component -> component + '\t' + components.getOrDefault(component, ""))
                        .toList()
        );
        return new CacheFingerprint(overall, components);
    }

    private String hashRuleAndConfig(List<LintRule> rules, LintOptions options) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "analysis-cache-v4-rule-config");
            update(digest, Boolean.toString(options.honorInlineSuppressions()));
            update(digest, Boolean.toString(options.assumeCentralizedSecurity()));
            update(digest, Boolean.toString(options.autoDetectCentralizedSecurity()));
            update(digest, Boolean.toString(options.autoDetectProjectWideKeyGenerator()));
            options.customSecurityAnnotations().stream()
                    .sorted()
                    .forEach(annotation -> update(digest, "custom-security-annotation\t" + annotation));
            options.cacheDefaultKeyCacheNames().stream()
                    .sorted()
                    .forEach(cacheName -> update(digest, "cache-default-key\t" + cacheName));
            updateClass(digest, ProjectLinter.class);
            updateClass(digest, InlineSuppressions.class);
            updateClass(digest, JavaSourceInspector.class);

            rules.stream()
                    .sorted(Comparator.comparing(LintRule::id))
                    .forEach(rule -> {
                        update(digest, rule.id());
                        update(digest, rule.title());
                        update(digest, rule.description());
                        update(digest, rule.severity().name());
                        update(digest, rule.implementationIdentity());
                        updateClass(digest, rule.implementationClass());
                    });
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String hashEntries(String namespace, List<String> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "analysis-cache-v4");
            update(digest, namespace);
            entries.stream()
                    .sorted()
                    .forEach(entry -> update(digest, entry));
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void appendAnalysis(StringBuilder builder, CachedFileAnalysis analysis) {
        builder.append("file\t")
                .append(encode(analysis.relativePath())).append('\t')
                .append(analysis.contentHash()).append('\t')
                .append(analysis.suppressedIssueCount()).append('\t')
                .append(encode(analysis.moduleId())).append(System.lineSeparator());
        for (String parseProblem : analysis.parseProblemMessages()) {
            builder.append("parse\t")
                    .append(encode(analysis.relativePath())).append('\t')
                    .append(encode(parseProblem)).append(System.lineSeparator());
        }
        for (CachedFileAnalysis.CachedIssue issue : analysis.issues()) {
            builder.append("issue\t")
                    .append(encode(analysis.relativePath())).append('\t')
                    .append(issue.ruleId()).append('\t')
                    .append(issue.severity()).append('\t')
                    .append(issue.line()).append('\t')
                    .append(encode(issue.message())).append(System.lineSeparator());
        }
    }

    private boolean isCacheDataRecord(String recordType) {
        return "file".equals(recordType) || "parse".equals(recordType) || "issue".equals(recordType);
    }

    private void appendCacheRecord(Map<String, MutableAnalysis> analyses, String line) {
        if (line == null || line.isBlank() || line.startsWith("#")) {
            return;
        }
        String[] parts = line.split("\t");
        if (parts.length == 0) {
            return;
        }
        switch (parts[0]) {
            case "file" -> {
                if (parts.length >= 4) {
                    String relativePath = decode(parts[1]);
                    String moduleId = parts.length >= 5 ? decode(parts[4]) : ".";
                    analyses.put(relativePath, new MutableAnalysis(relativePath, parts[2], Long.parseLong(parts[3]), moduleId));
                }
            }
            case "parse" -> {
                if (parts.length >= 3) {
                    String relativePath = decode(parts[1]);
                    analyses.computeIfAbsent(relativePath, key -> new MutableAnalysis(relativePath, "", 0L, "."))
                            .parseProblemMessages.add(decode(parts[2]));
                }
            }
            case "issue" -> {
                if (parts.length >= 6) {
                    String relativePath = decode(parts[1]);
                    analyses.computeIfAbsent(relativePath, key -> new MutableAnalysis(relativePath, "", 0L, "."))
                            .issues.add(new CachedFileAnalysis.CachedIssue(
                                    parts[2],
                                    LintSeverity.valueOf(parts[3]),
                                    Integer.parseInt(parts[4]),
                                    decode(parts[5])
                            ));
                }
            }
            default -> {
            }
        }
    }

    private void updateClass(MessageDigest digest, Class<?> type) {
        update(digest, type.getName());
        String resourceName = type.getName().replace('.', '/') + ".class";
        try (java.io.InputStream stream = type.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                return;
            }
            byte[] bytes = stream.readAllBytes();
            digest.update(bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load class bytes for " + type.getName(), exception);
        }
    }

    private void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }

    private List<String> invalidationReasons(CacheFingerprint currentFingerprint, Map<String, String> cachedComponents) {
        if (cachedComponents.isEmpty()) {
            return List.of(CACHE_REASON_CACHE_METADATA_UNAVAILABLE);
        }
        List<String> reasons = new ArrayList<>();
        for (String component : FINGERPRINT_COMPONENT_ORDER) {
            String current = currentFingerprint.components().get(component);
            String cached = cachedComponents.get(component);
            if (current == null || cached == null || !current.equals(cached)) {
                reasons.add(component);
            }
        }
        if (reasons.isEmpty()) {
            reasons.add(CACHE_REASON_CACHE_METADATA_UNAVAILABLE);
        }
        return reasons;
    }

    public record CacheFingerprint(String value, Map<String, String> components) {

        public CacheFingerprint {
            value = value == null ? "" : value;
            components = Map.copyOf(components);
        }
    }

    public record CacheState(Map<String, CachedFileAnalysis> analyses, List<String> missReasons) {

        public CacheState {
            analyses = Map.copyOf(analyses);
            missReasons = List.copyOf(missReasons);
        }

        public static CacheState empty(List<String> missReasons) {
            return new CacheState(Map.of(), missReasons);
        }
    }

    private static final class MutableAnalysis {

        private final String relativePath;
        private final String contentHash;
        private final long suppressedIssueCount;
        private final String moduleId;
        private final List<CachedFileAnalysis.CachedIssue> issues = new ArrayList<>();
        private final List<String> parseProblemMessages = new ArrayList<>();

        private MutableAnalysis(String relativePath, String contentHash, long suppressedIssueCount, String moduleId) {
            this.relativePath = relativePath;
            this.contentHash = contentHash;
            this.suppressedIssueCount = suppressedIssueCount;
            this.moduleId = moduleId;
        }
    }
}
