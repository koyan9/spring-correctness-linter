/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSemanticFactsTest {

    @TempDir
    Path tempDir;

    @Test
    void exposesTypeAndMethodSemanticFacts() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("SemanticDemo.java"), """
                package demo;

                import jakarta.annotation.PostConstruct;
                import org.springframework.beans.factory.InitializingBean;
                import org.springframework.cache.annotation.CacheConfig;
                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.context.event.EventListener;
                import org.springframework.scheduling.annotation.Async;
                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.security.access.prepost.PostAuthorize;
                import org.springframework.transaction.annotation.Propagation;
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.transaction.event.TransactionalEventListener;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @Transactional
                @CacheConfig(keyGenerator = "demoKeyGenerator")
                class SemanticDemo implements InitializingBean {

                    @GetMapping("/demo")
                    public String endpoint() {
                        return "ok";
                    }

                    @GetMapping("/secure")
                    @PostAuthorize("returnObject != null")
                    public String securedEndpoint() {
                        return "ok";
                    }

                    @Async
                    public void asyncMethod() {
                    }

                    @Scheduled(fixedRate = 1000)
                    public void scheduledMethod() {
                    }

                    @Cacheable(cacheNames = "demo", keyGenerator = "demoKeyGenerator")
                    public String cacheableWithKeyGenerator(String id) {
                        return id;
                    }

                    @Cacheable(cacheNames = "demo")
                    public String cacheableWithoutKey(String id) {
                        return id;
                    }

                    @Cacheable(cacheNames = "demo")
                    public String cacheableWithoutArgs() {
                        return "ok";
                    }

                    @Transactional(propagation = Propagation.REQUIRES_NEW)
                    public void transactionalRequiresNew() {
                    }

                    @EventListener
                    public void eventListener(Object event) {
                    }

                    @TransactionalEventListener
                    public void transactionalEventListener(Object event) {
                    }

                    @PostConstruct
                    public void postConstruct() {
                    }

                    @Override
                    public void afterPropertiesSet() {
                    }
                }
                """);

        ProjectContext context = ProjectContext.load(tempDir, tempDir.resolve("src/main/java"));
        SourceUnit sourceUnit = context.sourceUnits().get(0);
        SpringSemanticFacts facts = SpringSemanticFacts.forSourceUnit(sourceUnit, context);

        TypeDeclaration<?> typeDeclaration = sourceUnit.structure().typeDeclarations().get(0);
        TypeSemanticFacts typeFacts = facts.typeFacts(typeDeclaration);

        assertTrue(typeFacts.controller());
        assertTrue(typeFacts.isWebController());
        assertTrue(typeFacts.transactional());
        assertTrue(typeFacts.hasTransactionalBoundary());
        assertFalse(typeFacts.async());
        assertFalse(typeFacts.hasAsyncBoundary());
        assertFalse(typeFacts.isProfileScoped());
        assertFalse(typeFacts.isProfileScopedController());
        assertFalse(typeFacts.hasExplicitSecurityIntent());
        assertFalse(typeFacts.hasConflictingConditionalBeanAnnotations());

        MethodDeclaration endpoint = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("endpoint")).findFirst().orElseThrow();
        MethodDeclaration securedEndpoint = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("securedEndpoint")).findFirst().orElseThrow();
        MethodDeclaration asyncMethod = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("asyncMethod")).findFirst().orElseThrow();
        MethodDeclaration scheduledMethod = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("scheduledMethod")).findFirst().orElseThrow();
        MethodDeclaration eventListener = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("eventListener")).findFirst().orElseThrow();
        MethodDeclaration transactionalEventListener = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("transactionalEventListener")).findFirst().orElseThrow();
        MethodDeclaration postConstruct = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("postConstruct")).findFirst().orElseThrow();
        MethodDeclaration afterPropertiesSet = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("afterPropertiesSet")).findFirst().orElseThrow();
        MethodDeclaration cacheableWithKeyGenerator = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("cacheableWithKeyGenerator")).findFirst().orElseThrow();
        MethodDeclaration cacheableWithoutKey = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("cacheableWithoutKey")).findFirst().orElseThrow();
        MethodDeclaration cacheableWithoutArgs = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("cacheableWithoutArgs")).findFirst().orElseThrow();
        MethodDeclaration transactionalRequiresNew = sourceUnit.structure().methods().stream().filter(method -> method.getNameAsString().equals("transactionalRequiresNew")).findFirst().orElseThrow();

        MethodSemanticFacts endpointFacts = facts.methodFacts(typeDeclaration, endpoint);
        MethodSemanticFacts securedEndpointFacts = facts.methodFacts(typeDeclaration, securedEndpoint);
        MethodSemanticFacts asyncFacts = facts.methodFacts(typeDeclaration, asyncMethod);
        MethodSemanticFacts scheduledFacts = facts.methodFacts(typeDeclaration, scheduledMethod);
        MethodSemanticFacts eventFacts = facts.methodFacts(typeDeclaration, eventListener);
        MethodSemanticFacts transactionalEventFacts = facts.methodFacts(typeDeclaration, transactionalEventListener);
        MethodSemanticFacts postConstructFacts = facts.methodFacts(typeDeclaration, postConstruct);
        MethodSemanticFacts afterPropertiesFacts = facts.methodFacts(typeDeclaration, afterPropertiesSet);
        MethodSemanticFacts cacheFacts = facts.methodFacts(typeDeclaration, cacheableWithKeyGenerator);
        MethodSemanticFacts cacheWithoutKeyFacts = facts.methodFacts(typeDeclaration, cacheableWithoutKey);
        MethodSemanticFacts cacheWithoutArgsFacts = facts.methodFacts(typeDeclaration, cacheableWithoutArgs);
        MethodSemanticFacts requiresNewFacts = facts.methodFacts(typeDeclaration, transactionalRequiresNew);

        assertTrue(endpointFacts.requestMapping());
        assertTrue(endpointFacts.isPublicRequestMapping());
        assertTrue(securedEndpointFacts.hasExplicitSecurityIntent());
        assertTrue(asyncFacts.async());
        assertTrue(asyncFacts.hasAsyncBoundary());
        assertTrue(asyncFacts.isAsyncVoidMethod());
        assertFalse(asyncFacts.isAsyncPrivateMethod());
        assertFalse(asyncFacts.hasConflictingConditionalBeanAnnotations());
        assertTrue(scheduledFacts.scheduled());
        assertTrue(scheduledFacts.hasSchedulingBoundary());
        assertFalse(scheduledFacts.isScheduledMethodWithParameters());
        assertFalse(scheduledFacts.isScheduledAsyncBoundary());
        assertTrue(scheduledFacts.isScheduledTransactionalBoundary(typeFacts.hasTransactionalBoundary()));
        assertTrue(eventFacts.eventListener());
        assertTrue(eventFacts.hasEventListenerBoundary());
        assertTrue(eventFacts.isEventListenerTransactionalBoundary(typeFacts.hasTransactionalBoundary()));
        assertTrue(transactionalEventFacts.transactionalEventListener());
        assertTrue(transactionalEventFacts.hasTransactionalEventListenerBoundary());
        assertFalse(transactionalEventFacts.hasTransactionalEventBoundaryConflict());
        assertFalse(transactionalEventFacts.isEventListenerTransactionalBoundary(typeFacts.hasTransactionalBoundary()));
        assertTrue(postConstructFacts.initializationCallback());
        assertTrue(postConstructFacts.isLifecycleBoundary());
        assertTrue(afterPropertiesFacts.initializationCallback());
        assertTrue(afterPropertiesFacts.isLifecycleBoundary());
        assertFalse(endpointFacts.startupLifecycleMethod());
        assertFalse(endpointFacts.hasExplicitSecurityIntent());
        assertTrue(cacheFacts.hasExplicitCacheKeyStrategy());
        assertFalse(cacheFacts.shouldDeclareExplicitCacheKey());
        assertTrue(cacheWithoutKeyFacts.hasExplicitCacheKeyStrategy());
        assertFalse(cacheWithoutKeyFacts.shouldDeclareExplicitCacheKey());
        assertFalse(cacheWithoutArgsFacts.shouldDeclareExplicitCacheKey());
        assertTrue(requiresNewFacts.hasHighRiskTransactionPropagation());
        assertTrue(requiresNewFacts.highRiskTransactionPropagationName().equals("REQUIRES_NEW"));
        assertSame(facts, context.springFacts(sourceUnit));
    }
}
