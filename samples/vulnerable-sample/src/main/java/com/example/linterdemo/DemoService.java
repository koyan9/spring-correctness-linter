/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.linterdemo;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
class DemoService {

    @Async
    public void asyncRefresh() {
    }

    @Async
    private void asyncPrivateRefresh() {
    }

    public void outer() {
        this.inner();
    }

    @Transactional
    public void inner() {
    }

    @Transactional
    private void privateTransactionalRefresh() {
    }

    @Transactional
    public final void finalTransactionalRefresh() {
    }

    @EventListener
    @Transactional
    public void handlePatientEvent(Object event) {
    }

    @TransactionalEventListener
    @Transactional
    public void handleCommittedPatientEvent(Object event) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNewRefresh() {
    }
}