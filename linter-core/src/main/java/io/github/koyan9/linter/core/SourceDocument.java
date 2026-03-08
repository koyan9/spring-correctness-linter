/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record SourceDocument(Path path, String content, String contentHash, String moduleId) {

    public SourceDocument {
        contentHash = contentHash == null || contentHash.isBlank() ? sha256(content) : contentHash;
        moduleId = moduleId == null || moduleId.isBlank() ? "." : moduleId;
    }

    public SourceDocument(Path path, String content) {
        this(path, content, sha256(content), ".");
    }

    public String relativePath(Path projectRoot) {
        return projectRoot.relativize(path).toString().replace('\\', '/');
    }

    public SourceUnit toSourceUnit() {
        return new SourceUnit(path, content);
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
