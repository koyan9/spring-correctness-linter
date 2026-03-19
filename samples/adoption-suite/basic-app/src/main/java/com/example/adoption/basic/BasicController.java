/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.adoption.basic;

import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BasicController {

    @GetMapping("/open")
    public String open() {
        return "ok";
    }

    @Async
    public void refreshAsync() {
    }
}
