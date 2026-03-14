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
        return toSourceUnit(null);
    }

    public SourceUnit toSourceUnit(JavaSourceInspector.ParseOutcome parseOutcome) {
        if (parseOutcome == null) {
            return new SourceUnit(path, content);
        }
        return new SourceUnit(path, content, parseOutcome);
    }

    static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (byte current : hashed) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }
}
