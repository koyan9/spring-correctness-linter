/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.adoption.cache;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

@Component
class CacheConventionConfig implements KeyGenerator {

    @Override
    public Object generate(Object target, java.lang.reflect.Method method, Object... params) {
        return params.length;
    }
}

class CacheService {

    @Cacheable(cacheNames = "users")
    public String load(String id) {
        return id;
    }
}
