/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.linterdemo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ConditionalConfig {

    @Bean
    @ConditionalOnBean(name = "demoBean")
    @ConditionalOnMissingBean(name = "demoBean")
    Object conflictingBean() {
        return new Object();
    }
}