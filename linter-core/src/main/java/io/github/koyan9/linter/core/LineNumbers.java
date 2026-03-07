/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

public final class LineNumbers {

    private LineNumbers() {
    }

    public static int lineNumberAt(String content, int index) {
        int normalizedIndex = Math.max(0, Math.min(index, content.length()));
        int line = 1;
        for (int cursor = 0; cursor < normalizedIndex; cursor++) {
            if (content.charAt(cursor) == '\n') {
                line++;
            }
        }
        return line;
    }
}
