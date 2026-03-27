/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.linterdemo;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
final class FinalCacheableService {

    @Cacheable(cacheNames = "demo", key = "#id")
    public String refresh(String id) {
        return id;
    }
}
