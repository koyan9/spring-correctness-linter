/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import java.nio.file.Path;
import java.util.List;

public record SourceParseProblem(Path file, List<String> messages) {

    public SourceParseProblem {
        messages = List.copyOf(messages);
    }
}
