/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisCacheStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsMissReasonsFromMetadataWithoutParsingRemainingCacheEntries() throws Exception {
        Path cacheFile = tempDir.resolve("analysis-cache.txt");
        Files.writeString(cacheFile, """
                # spring-correctness-linter analysis cache v4
                fingerprint\told-fingerprint
                component\trule-or-config-changed\told-rule
                component\tsource-roots-changed\tsame-roots
                component\tannotation-or-type-context-changed\tsame-types
                component\tauto-detect-context-changed\tsame-auto
                file\t%s\thash\t0\t%s
                issue\t%s\tRULE_A\tWARNING\t7\t%s
                """.formatted(
                encode("src/main/java/demo/Demo.java"),
                encode("."),
                encode("src/main/java/demo/Demo.java"),
                encode("message")
        ));

        AnalysisCacheStore store = new AnalysisCacheStore();
        AnalysisCacheStore.CacheState state = store.load(
                cacheFile,
                new AnalysisCacheStore.CacheFingerprint(
                        "new-fingerprint",
                        Map.of(
                                AnalysisCacheStore.CACHE_REASON_RULE_OR_CONFIG_CHANGED, "new-rule",
                                AnalysisCacheStore.CACHE_REASON_SOURCE_ROOTS_CHANGED, "same-roots",
                                AnalysisCacheStore.CACHE_REASON_ANNOTATION_OR_TYPE_CONTEXT_CHANGED, "same-types",
                                AnalysisCacheStore.CACHE_REASON_AUTO_DETECT_CONTEXT_CHANGED, "same-auto"
                        )
                )
        );

        assertTrue(state.analyses().isEmpty());
        assertEquals(1, state.missReasons().size());
        assertEquals(AnalysisCacheStore.CACHE_REASON_RULE_OR_CONFIG_CHANGED, state.missReasons().get(0));
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
