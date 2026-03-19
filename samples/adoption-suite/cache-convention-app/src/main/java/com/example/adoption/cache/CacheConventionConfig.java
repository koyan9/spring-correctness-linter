/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.adoption.cache;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.interceptor.KeyGenerator;

class CacheConventionConfig extends CachingConfigurerSupport {

    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> params.length;
    }
}

class CacheService {

    @Cacheable(cacheNames = "users")
    public String load(String id) {
        return id;
    }
}
