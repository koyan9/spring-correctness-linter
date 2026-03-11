/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.modulea;

import org.springframework.transaction.annotation.Transactional;

public class BaseTransactionalService {

    @Transactional
    public void process(String id) {
    }
}
