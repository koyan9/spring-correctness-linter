/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

public enum RuleDomain {
    ASYNC("Async"),
    LIFECYCLE("Lifecycle"),
    SCHEDULED("Scheduled"),
    CACHE("Cache"),
    WEB("Web"),
    TRANSACTION("Transaction"),
    EVENTS("Events"),
    CONFIGURATION("Configuration"),
    GENERAL("General");

    private final String displayName;

    RuleDomain(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
