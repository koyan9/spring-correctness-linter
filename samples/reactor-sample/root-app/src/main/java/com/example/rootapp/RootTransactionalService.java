/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.rootapp;

import com.example.modulea.BaseTransactionalService;

public class RootTransactionalService extends BaseTransactionalService {

    public void run(String id) {
        process(id);
    }
}
