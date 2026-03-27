/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.linterdemo;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
class DemoService implements InitializingBean, ApplicationRunner, SmartInitializingSingleton {

    @Async
    public void asyncRefresh() {
    }

    @Async
    public String asyncStringRefresh() {
        return "ok";
    }

    @Async
    private void asyncPrivateRefresh() {
    }

    @Async
    public final void asyncFinalRefresh() {
    }

    @Cacheable(cacheNames = "demo", key = "#id")
    private String privateCachedValue(String id) {
        return id;
    }

    public String warmCache(String id) {
        return loadCachedValue(id);
    }

    @Cacheable(cacheNames = "demo", key = "#id")
    public String loadCachedValue(String id) {
        return id;
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

    @Scheduled
    public void missingScheduleTrigger() {
    }

    @Scheduled(fixedRate = 1_000, cron = "0 * * * * *")
    public void conflictingScheduleTrigger() {
    }

    @Scheduled(fixedDelay = 1_000)
    public void scheduledWithArgument(String tenantId) {
    }

    @Scheduled(fixedRate = 1_000)
    public String scheduledReturningValue() {
        return "done";
    }

    @Scheduled(fixedRate = 1_000)
    @Async
    public void asyncScheduledRefresh() {
    }

    @Scheduled(fixedRate = 1_000)
    @Scheduled(cron = "0 * * * * *")
    public void repeatedScheduledRefresh() {
    }

    @Scheduled(fixedDelay = 0)
    public void zeroDelayScheduledRefresh() {
    }

    @Scheduled(fixedDelay = 1_000)
    @Transactional
    public void transactionalScheduledRefresh() {
    }

    @PostConstruct
    @Async
    public void initializeAsyncRefresh() {
    }

    @Override
    @Transactional
    public void afterPropertiesSet() {
    }

    @Override
    @Async
    public void run(ApplicationArguments args) {
    }

    @Override
    @Transactional
    public void afterSingletonsInstantiated() {
    }
}
