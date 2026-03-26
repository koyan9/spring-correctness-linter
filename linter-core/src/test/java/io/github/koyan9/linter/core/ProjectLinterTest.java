/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import io.github.koyan9.linter.core.rules.SpringBootRuleSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectLinterTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsDefaultSpringRules() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("DemoController.java"), """
                package demo;

                import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
                import org.springframework.cache.annotation.CacheEvict;
                import org.springframework.cache.annotation.CachePut;
                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Profile;
                import org.springframework.context.event.EventListener;
                import org.springframework.transaction.event.TransactionalEventListener;
                import org.springframework.scheduling.annotation.Async;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Propagation;
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @Profile(\"test\")
                class DemoController {

                    @GetMapping(\"/demo\")
                    public String openEndpoint() {
                        return \"ok\";
                    }
                }

                @Service
                class DemoService {

                    @Async
                    public void asyncWork() {
                    }

                    @Async
                    private void asyncPrivateWork() {
                    }

                    @Cacheable(cacheNames = \"demo\")
                    public String load(String id) {
                        return id;
                    }

                    @Cacheable(cacheNames = \"demo\")
                    @CachePut(cacheNames = \"demo\")
                    public String refresh(String id) {
                        return id;
                    }

                    public void outer() {
                        this.inner();
                    }

                    @Transactional
                    public void inner() {
                    }

                    @Transactional
                    private void privateTransactional() {
                    }

                    @Transactional
                    public final void finalTransactional() {
                    }

                    @EventListener
                    @Transactional
                    public void onEvent(Object event) {
                    }

                    @TransactionalEventListener
                    @Transactional
                    public void onTransactionalEvent(Object event) {
                    }

                    @Transactional(propagation = Propagation.REQUIRES_NEW)
                    public void requiresNewWork() {
                    }
                }

                class DemoConfig {

                    @Bean
                    @ConditionalOnBean(name = \"a\")
                    @ConditionalOnMissingBean(name = \"a\")
                    public Object conflictingBean() {
                        return new Object();
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertEquals(28, report.rules().size());
        assertTrue(issueIds.contains("SPRING_ASYNC_VOID"));
        assertTrue(issueIds.contains("SPRING_ASYNC_PRIVATE_METHOD"));
        assertTrue(issueIds.contains("SPRING_CACHEABLE_KEY"));
        assertTrue(issueIds.contains("SPRING_CACHE_COMBINATION_RISK"));
        assertTrue(issueIds.contains("SPRING_PROFILE_CONTROLLER"));
        assertTrue(issueIds.contains("SPRING_TX_SELF_INVOCATION"));
        assertTrue(issueIds.contains("SPRING_TX_PRIVATE_METHOD"));
        assertTrue(issueIds.contains("SPRING_TX_FINAL_METHOD"));
        assertTrue(issueIds.contains("SPRING_EVENT_LISTENER_TRANSACTIONAL"));
        assertTrue(issueIds.contains("SPRING_TRANSACTIONAL_EVENT_LISTENER"));
        assertTrue(issueIds.contains("SPRING_TX_HIGH_RISK_PROPAGATION"));
        assertTrue(issueIds.contains("SPRING_CONDITIONAL_BEAN_CONFLICT"));
        assertTrue(issueIds.contains("SPRING_ENDPOINT_SECURITY"));
    }


    @Test
    void detectsAsyncSelfInvocation() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncSelfInvocation.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncSelfInvocation {

                    public void outer() {
                        asyncWork();
                    }

                    @Async
                    public String asyncWork() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> issues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_ASYNC_SELF_INVOCATION"))
                .toList();

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).message().contains("asyncWork"));
    }

    @Test
    void flagsMethodReferencesForAsyncSelfInvocation() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncSelfInvocationMethodRef.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncSelfInvocationMethodRef {

                    public void outer() {
                        Runnable task = this::asyncWork;
                        task.run();
                    }

                    @Async
                    public void asyncWork() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> issues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_ASYNC_SELF_INVOCATION"))
                .toList();

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).message().contains("asyncWork"));
    }


    @Test
    void detectsAsyncTransactionalBoundary() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncTransactionalBoundary.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;
                import org.springframework.transaction.annotation.Transactional;

                @Transactional
                class AsyncTransactionalBoundary {

                    @Async
                    public void runAsyncTransactional() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> issues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_ASYNC_TRANSACTIONAL_BOUNDARY"))
                .toList();

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).message().contains("runAsyncTransactional"));
    }

    @Test
    void detectsAsyncFinalMethod() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncFinalDemo.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncFinalDemo {

                    @Async
                    public final void runAsync() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> issues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_ASYNC_FINAL_METHOD"))
                .toList();

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).message().contains("runAsync"));
    }

    @Test
    void detectsFinalTransactionalClassWithoutInterface() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("FinalTransactionalDemo.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                interface Worker {
                    void doWork();
                }

                final class FinalTransactionalService {

                    @Transactional
                    public void save() {
                    }
                }

                @Transactional
                final class InterfaceTransactionalService implements Worker {

                    @Override
                    public void doWork() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> issues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_FINAL_CLASS"))
                .toList();

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).message().contains("FinalTransactionalService"));
    }
    @Test
    void writesSarifReport() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Path output = tempDir.resolve("reports/lint-report.sarif.json");

        new ReportWriter().writeSarif(report, output);
        String sarif = Files.readString(output);

        assertTrue(sarif.contains("\"version\": \"2.1.0\""));
        assertTrue(sarif.contains("SPRING_ASYNC_VOID"));
        assertTrue(sarif.contains("src/main/java/demo/AsyncOnly.java"));
        assertTrue(sarif.contains("\"moduleId\": \"src/main/java\""));
    }

    @Test
    void honorsInlineSuppressionAndBaseline() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    // spring-correctness-linter:disable-next-line SPRING_ASYNC_VOID reason: legacy async contract
                    @Async
                    public void suppressedAsync() {
                    }

                    @Async
                    public void baselineAsync() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        Path baselineFile = tempDir.resolve("spring-correctness-linter-baseline.txt");

        LintAnalysisResult firstRun = linter.analyze(
                tempDir,
                tempDir.resolve("src/main/java"),
                new LintOptions(true, false, baselineFile)
        );

        assertEquals(1, firstRun.report().issueCount());
        assertEquals(1, firstRun.report().suppressedIssueCount());
        assertEquals(0, firstRun.report().baselineMatchedIssueCount());
        assertEquals(0, firstRun.report().staleBaselineEntryCount());

        new BaselineStore().write(baselineFile, tempDir, firstRun.baselineCandidates());

        LintAnalysisResult secondRun = linter.analyze(
                tempDir,
                tempDir.resolve("src/main/java"),
                new LintOptions(true, true, baselineFile)
        );

        assertEquals(0, secondRun.report().issueCount());
        assertEquals(1, secondRun.report().suppressedIssueCount());
        assertEquals(1, secondRun.report().baselineMatchedIssueCount());
        assertEquals(0, secondRun.report().staleBaselineEntryCount());
        assertEquals(0, secondRun.baselineDiffReport().newIssues().size());
        assertEquals(1, secondRun.baselineDiffReport().matchedEntries().size());
        assertEquals(0, secondRun.baselineDiffReport().staleEntries().size());

        Files.writeString(baselineFile, Files.readString(baselineFile) + "SPRING_FAKE\t1\tZHVtbXkvRHVtbXkuamF2YQ\tZHVtbXk\n");
        LintAnalysisResult thirdRun = linter.analyze(
                tempDir,
                tempDir.resolve("src/main/java"),
                new LintOptions(true, true, baselineFile)
        );

        assertEquals(1, thirdRun.report().staleBaselineEntryCount());
        assertEquals(1, thirdRun.baselineDiffReport().staleEntries().size());

        Path diffFile = tempDir.resolve("reports/baseline-diff.json");
        new ReportWriter().writeBaselineDiff(thirdRun.baselineDiffReport(), diffFile);
        String baselineDiffJson = Files.readString(diffFile);
        assertTrue(baselineDiffJson.contains("staleBaselineCount"));
        assertTrue(baselineDiffJson.contains("\"moduleSummaries\""));
        assertTrue(baselineDiffJson.contains("\"module\""));
    }

    @Test
    void acceptsLegacyMedicalLinterSuppressionPrefix() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("LegacyPrefixAsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class LegacyPrefixAsyncOnly {

                    // medical-linter:disable-next-line SPRING_ASYNC_VOID reason: legacy prefix compatibility
                    @Async
                    public void suppressedAsync() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintAnalysisResult result = linter.analyze(tempDir, tempDir.resolve("src/main/java"), LintOptions.defaults());

        assertEquals(0, result.report().issueCount());
        assertEquals(1, result.report().suppressedIssueCount());
    }

    @Test
    void supportsMethodAndTypeScopedSuppressions() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ScopedSuppressionDemo.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                // spring-correctness-linter:disable-next-type SPRING_ENDPOINT_SECURITY reason: internal controller sample
                @RestController
                class InternalController {

                    @GetMapping(\"/internal\")
                    public String internal() {
                        return \"ok\";
                    }
                }

                class AsyncDemo {

                    // spring-correctness-linter:disable-next-method SPRING_ASYNC_PRIVATE_METHOD reason: legacy private async adapter
                    @Async
                    private void asyncPrivateWork() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_ENDPOINT_SECURITY"));
        assertFalse(issueIds.contains("SPRING_ASYNC_PRIVATE_METHOD"));
    }

    @Test
    void detectsCacheCombinationRisk() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("CacheCombo.java"), """
                package demo;

                import org.springframework.cache.annotation.CachePut;
                import org.springframework.cache.annotation.Cacheable;

                class CacheCombo {

                    @Cacheable(cacheNames = \"a\")
                    @CachePut(cacheNames = \"a\")
                    public String refresh(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertTrue(issueIds.contains("SPRING_CACHE_COMBINATION_RISK"));
    }

    @Test
    void flagsMissingCacheKeyForOverloadedMethods() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("CacheOverloads.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;

                class CacheOverloads {

                    @Cacheable(cacheNames = "demo", key = "#id")
                    public String load(String id) {
                        return id;
                    }

                    @Cacheable(cacheNames = "demo")
                    public String load(String id, int version) {
                        return id + version;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> cacheIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_CACHEABLE_KEY"))
                .toList();

        assertEquals(1, cacheIssues.size());
        assertTrue(cacheIssues.get(0).message().contains("load"));
    }

    @Test
    void flagsBlankCacheableKeyGenerator() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("BlankKeyGenerator.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;

                class BlankKeyGenerator {

                    @Cacheable(cacheNames = "demo", keyGenerator = "")
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> cacheIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_CACHEABLE_KEY"))
                .toList();

        assertEquals(1, cacheIssues.size());
        assertTrue(cacheIssues.get(0).message().contains("load"));
    }

    @Test
    void flagsBlankCacheConfigKeyGenerator() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("BlankCacheConfig.java"), """
                package demo;

                import org.springframework.cache.annotation.CacheConfig;
                import org.springframework.cache.annotation.Cacheable;

                @CacheConfig(keyGenerator = "")
                class BlankCacheConfig {

                    @Cacheable(cacheNames = "demo")
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> cacheIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_CACHEABLE_KEY"))
                .toList();

        assertEquals(1, cacheIssues.size());
        assertTrue(cacheIssues.get(0).message().contains("load"));
    }

    @Test
    void allowsCacheConfigNamesInDefaultKeyAllowlist() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("CacheConfigAllowlist.java"), """
                package demo;

                import org.springframework.cache.annotation.CacheConfig;
                import org.springframework.cache.annotation.Cacheable;

                @CacheConfig(cacheNames = "safe")
                class CacheConfigAllowlist {

                    @Cacheable
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintOptions options = LintOptions.defaults().withCacheDefaultKeyCacheNames(Set.of("safe"));
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_CACHEABLE_KEY"));
    }

    @Test
    void allowsComposedCacheNamesInDefaultKeyAllowlist() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ComposedCacheAllowlist.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.core.annotation.AliasFor;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @Cacheable
                @interface AllowedCacheable {

                    @AliasFor(annotation = Cacheable.class, attribute = "cacheNames")
                    String[] cacheNames() default {};
                }

                class CacheService {

                    @AllowedCacheable(cacheNames = "safe")
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintOptions options = LintOptions.defaults().withCacheDefaultKeyCacheNames(Set.of("safe"));
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_CACHEABLE_KEY"));
    }

    @Test
    void allowsComposedCacheConfigNamesInDefaultKeyAllowlist() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ComposedCacheConfigAllowlist.java"), """
                package demo;

                import org.springframework.cache.annotation.CacheConfig;
                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.core.annotation.AliasFor;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                @CacheConfig
                @interface CacheConfigNames {

                    @AliasFor(annotation = CacheConfig.class, attribute = "cacheNames")
                    String[] cacheNames() default {};
                }

                @CacheConfigNames(cacheNames = "safe")
                class CacheService {

                    @Cacheable
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintOptions options = LintOptions.defaults().withCacheDefaultKeyCacheNames(Set.of("safe"));
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_CACHEABLE_KEY"));
    }

    @Test
    void allowsSingleMemberCacheableValueInDefaultKeyAllowlist() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("SingleMemberCacheAllowlist.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;

                class CacheService {

                    @Cacheable("safe")
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintOptions options = LintOptions.defaults().withCacheDefaultKeyCacheNames(Set.of("safe"));
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_CACHEABLE_KEY"));
    }

    @Test
    void doesNotTreatCacheAllowlistAsSubstringMatch() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("SubstringCacheAllowlist.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;

                class CacheService {

                    @Cacheable(cacheNames = "users")
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintOptions options = LintOptions.defaults().withCacheDefaultKeyCacheNames(Set.of("user"));
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        List<LintIssue> cacheIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_CACHEABLE_KEY"))
                .toList();

        assertEquals(1, cacheIssues.size());
        assertTrue(cacheIssues.get(0).message().contains("load"));
    }

    @Test
    void autoDetectsProjectWideKeyGeneratorWhenEnabled() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ProjectKeyGeneratorConfig.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.cache.interceptor.KeyGenerator;
                import org.springframework.context.annotation.Bean;

                class ProjectKeyGeneratorConfig {

                    @Bean
                    KeyGenerator keyGenerator() {
                        return (target, method, params) -> params.length;
                    }
                }

                class CacheService {

                    @Cacheable(cacheNames = "demo")
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport defaultReport = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        assertTrue(defaultReport.issues().stream().anyMatch(issue -> issue.ruleId().equals("SPRING_CACHEABLE_KEY")));

        LintOptions options = LintOptions.defaults().withAutoDetectProjectWideKeyGenerator(true);
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        assertFalse(report.issues().stream().anyMatch(issue -> issue.ruleId().equals("SPRING_CACHEABLE_KEY")));
    }

    @Test
    void autoDetectsCachingConfigurerKeyGeneratorWhenEnabled() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("CachingConfigurerSupportConfig.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.cache.annotation.CachingConfigurer;
                import org.springframework.cache.interceptor.KeyGenerator;

                class CachingConfigurerSupportConfig implements CachingConfigurer {

                    @Override
                    public KeyGenerator keyGenerator() {
                        return (target, method, params) -> params.length;
                    }
                }

                class CacheService {

                    @Cacheable(cacheNames = "demo")
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport defaultReport = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        assertTrue(defaultReport.issues().stream().anyMatch(issue -> issue.ruleId().equals("SPRING_CACHEABLE_KEY")));

        LintOptions options = LintOptions.defaults().withAutoDetectProjectWideKeyGenerator(true);
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        assertFalse(report.issues().stream().anyMatch(issue -> issue.ruleId().equals("SPRING_CACHEABLE_KEY")));
    }

    @Test
    void recognizesPostAuthorizeEndpointsAndZeroArgCacheDefaults() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("SemanticAccuracyDemo.java"), """
                package demo;

                import org.springframework.cache.annotation.CacheConfig;
                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.security.access.prepost.PostAuthorize;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class SecureController {

                    @GetMapping("/secure")
                    @PostAuthorize("returnObject != null")
                    public String secure() {
                        return "ok";
                    }
                }

                class CacheDemo {

                    @Cacheable(cacheNames = "demo")
                    public String fixedValue() {
                        return "ok";
                    }

                    @Cacheable(cacheNames = "demo")
                    public String byId(String id) {
                        return id;
                    }
                }

                @CacheConfig(cacheNames = "config", keyGenerator = "demoKeyGenerator")
                class CacheConfigDemo {

                    @Cacheable
                    public String configuredLoad(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());
        List<LintIssue> cacheIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_CACHEABLE_KEY"))
                .toList();

        assertFalse(issueIds.contains("SPRING_ENDPOINT_SECURITY"));
        assertEquals(1, cacheIssues.size());
        assertTrue(cacheIssues.get(0).message().contains("byId"));
    }

    @Test
    void ignoresStringsAndCommentsThatPreviouslyLookedLikeMatches() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("FalsePositiveDemo.java"), """
                package demo;

                class FalsePositiveDemo {

                    public void outer() {
                        String text = \"this.inner() @Transactional @Async @Cacheable\";
                        // this.inner();
                        // @Async
                        System.out.println(text);
                    }

                    public void inner() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));

        assertEquals(0, report.issueCount());
    }

    @Test
    void toleratesBrokenJavaWithoutCrashing() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("Broken.java"), """
                package demo;

                class Broken {
                    public void run( {
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));

        assertEquals(0, report.issueCount());
        assertEquals(28, report.rules().size());
        assertEquals(1, report.parseProblemFileCount());
        assertTrue(report.parseProblems().get(0).file().endsWith(Path.of("src/main/java/demo/Broken.java")));
    }

    @Test
    void detectsClassLevelTransactionalEventListenerBoundary() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ClassLevelTransactionalListener.java"), """
                package demo;

                import org.springframework.context.event.EventListener;
                import org.springframework.transaction.annotation.Transactional;

                @Transactional
                class ClassLevelTransactionalListener {

                    @EventListener
                    public void onEvent(Object event) {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertTrue(issueIds.contains("SPRING_EVENT_LISTENER_TRANSACTIONAL"));
    }

    @Test
    void doesNotFlagSafeTransactionalEventListenerUsage() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("SafeTransactionalEventListener.java"), """
                package demo;

                import org.springframework.transaction.event.TransactionalEventListener;

                class SafeTransactionalEventListener {

                    @TransactionalEventListener
                    public void onEvent(Object event) {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_TRANSACTIONAL_EVENT_LISTENER"));
    }

    @Test
    void reusesIncrementalCacheForUnchangedFiles() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        Path cacheFile = tempDir.resolve("target/analysis-cache.txt");

        LintReport firstRun = linter.analyze(
                tempDir,
                tempDir.resolve("src/main/java"),
                new LintOptions(true, false, null, cacheFile, true)
        ).report();

        LintReport secondRun = linter.analyze(
                tempDir,
                tempDir.resolve("src/main/java"),
                new LintOptions(true, false, null, cacheFile, true)
        ).report();

        assertEquals(1, firstRun.issueCount());
        assertEquals(0, firstRun.cachedFileCount());
        assertEquals(1, secondRun.issueCount());
        assertEquals(1, secondRun.cachedFileCount());
        assertTrue(secondRun.runtimeMetrics().incrementalCacheEnabled());
        assertEquals("shared-file", secondRun.runtimeMetrics().cacheScope());
        assertEquals(1, secondRun.runtimeMetrics().cachedFileCount());
        assertEquals(0, secondRun.runtimeMetrics().analyzedFileCount());
    }

    @Test
    void invalidatesIncrementalCacheWhenRuleConfigurationChanges() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);

        Path cacheFile = tempDir.resolve("target/analysis-cache.txt");
        Path rootSourceDirectory = tempDir.resolve("src/main/java");

        ProjectLinter defaultLinter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport firstRun = defaultLinter.analyze(
                tempDir,
                rootSourceDirectory,
                new LintOptions(true, false, null, cacheFile, true)
        ).report();

        ProjectLinter severityOverrideLinter = new ProjectLinter(RuleSelection.configure(
                SpringBootRuleSet.defaultRules(),
                Set.of(),
                Set.of(),
                Map.of("SPRING_ASYNC_VOID", LintSeverity.ERROR)
        ));
        LintReport secondRun = severityOverrideLinter.analyze(
                tempDir,
                rootSourceDirectory,
                new LintOptions(true, false, null, cacheFile, true)
        ).report();

        assertEquals(0, firstRun.cachedFileCount());
        assertEquals(0, secondRun.cachedFileCount());
        assertNotEquals(firstRun.runtimeMetrics().analysisFingerprint(), secondRun.runtimeMetrics().analysisFingerprint());
        assertTrue(secondRun.issues().stream().anyMatch(issue -> issue.ruleId().equals("SPRING_ASYNC_VOID") && issue.severity() == LintSeverity.ERROR));
    }

    @Test
    void analyzesMultipleFilesWhenParallelAnalysisDisabled() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Files.writeString(sourceDirectory.resolve("PrivateAsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class PrivateAsyncOnly {

                    @Async
                    private void runAsync() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintOptions options = LintOptions.defaults()
                .withParallelFileAnalysis(false)
                .withAutoDetectCentralizedSecurity(false);
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertEquals(3, report.issueCount());
        assertEquals(2, report.runtimeMetrics().sourceFileCount());
        assertEquals(2, report.runtimeMetrics().analyzedFileCount());
        assertEquals(0, report.runtimeMetrics().cachedFileCount());
        assertTrue(issueIds.contains("SPRING_ASYNC_VOID"));
        assertTrue(issueIds.contains("SPRING_ASYNC_PRIVATE_METHOD"));
    }

    @Test
    void analyzesMultipleFilesWhenParallelismIsOne() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Files.writeString(sourceDirectory.resolve("TransactionalPrivateOnly.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                class TransactionalPrivateOnly {

                    @Transactional
                    private void runTransactional() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintOptions options = LintOptions.defaults()
                .withParallelFileAnalysis(true)
                .withFileAnalysisParallelism(1);
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertEquals(2, report.issueCount());
        assertEquals(2, report.runtimeMetrics().sourceFileCount());
        assertEquals(2, report.runtimeMetrics().analyzedFileCount());
        assertTrue(issueIds.contains("SPRING_ASYNC_VOID"));
        assertTrue(issueIds.contains("SPRING_TX_PRIVATE_METHOD"));
    }

    @Test
    void rejectsNegativeFileAnalysisParallelism() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> LintOptions.defaults().withFileAnalysisParallelism(-1)
        );

        assertTrue(exception.getMessage().contains("fileAnalysisParallelism"));
    }

    @Test
    void analyzesMultipleSourceRootsInOneRun() throws Exception {
        Path mainSourceDirectory = tempDir.resolve("src/main/java/demo");
        Path generatedSourceDirectory = tempDir.resolve("target/generated-sources/demo");
        Files.createDirectories(mainSourceDirectory);
        Files.createDirectories(generatedSourceDirectory);
        Files.writeString(mainSourceDirectory.resolve("AsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Files.writeString(generatedSourceDirectory.resolve("PrivateAsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class PrivateAsyncOnly {

                    @Async
                    private void runAsync() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(
                tempDir,
                List.of(tempDir.resolve("src/main/java"), tempDir.resolve("target/generated-sources")),
                new LintOptions(true, false, null)
        ).report();
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertEquals(2, report.sourceDirectoryCount());
        assertTrue(issueIds.contains("SPRING_ASYNC_VOID"));
        assertTrue(issueIds.contains("SPRING_ASYNC_PRIVATE_METHOD"));
    }

    @Test
    void ignoresMissingOrEmptySourceRootsInModuleMetrics() throws Exception {
        Path mainSourceDirectory = tempDir.resolve("src/main/java/demo");
        Path emptySourceDirectory = tempDir.resolve("empty-src");
        Files.createDirectories(mainSourceDirectory);
        Files.createDirectories(emptySourceDirectory);
        Files.writeString(mainSourceDirectory.resolve("AsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintAnalysisResult result = linter.analyzeSourceRoots(
                tempDir,
                List.of(
                        new SourceRoot(tempDir.resolve("src/main/java"), "main-module"),
                        new SourceRoot(emptySourceDirectory, "empty-module"),
                        new SourceRoot(tempDir.resolve("missing-src"), "missing-module")
                ),
                new LintOptions(true, false, null)
        );
        LintReport report = result.report();

        assertEquals(1, report.sourceDirectoryCount());
        assertEquals(1, report.moduleSummaries().size());
        assertEquals("main-module", report.moduleSummaries().get(0).moduleId());
        assertEquals(1, report.runtimeMetrics().moduleMetrics().size());
        assertEquals("main-module", report.runtimeMetrics().moduleMetrics().get(0).moduleId());
    }

    @Test
    void recognizesComposedAsyncAnnotationsAcrossFiles() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("BackgroundAsync.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @Async
                @interface BackgroundAsync {
                }
                """);
        Files.writeString(sourceDirectory.resolve("AsyncService.java"), """
                package demo;

                class AsyncService {

                    @BackgroundAsync
                    public void runAsync() {
                    }

                    @BackgroundAsync
                    private void runPrivateAsync() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertTrue(issueIds.contains("SPRING_ASYNC_VOID"));
        assertTrue(issueIds.contains("SPRING_ASYNC_PRIVATE_METHOD"));
    }

    @Test
    void detectsUnqualifiedTransactionalSelfInvocationButRespectsOverloadArity() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("TransactionalSelfInvocationDemo.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                class TransactionalSelfInvocationDemo {

                    public void outer() {
                        inner();
                    }

                    public void overloadedCaller() {
                        inner("safe");
                    }

                    @Transactional
                    public void inner() {
                    }

                    public void inner(String value) {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("inner"));
    }

    @Test
    void detectsClassLevelTransactionalSelfInvocation() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ClassLevelTransactionalSelfInvocation.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                @Transactional
                class ClassLevelTransactionalSelfInvocation {

                    public void outer() {
                        inner();
                        helper();
                    }

                    public void inner() {
                    }

                    private void helper() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("inner"));
    }

    @Test
    void detectsInheritedTransactionalSelfInvocation() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("InheritedSelfInvocation.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                class BaseService {

                    @Transactional
                    public void process(String id) {
                    }
                }

                class ChildService extends BaseService {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void detectsVarargsTransactionalSelfInvocation() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("VarargsSelfInvocation.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                class VarargsSelfInvocation {

                    public void outer() {
                        record("a", "b");
                    }

                    @Transactional
                    public void record(String... values) {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("record"));
    }

    @Test
    void detectsMultiLevelInheritedTransactionalSelfInvocation() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("MultiLevelInheritedSelfInvocation.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                class BaseService {

                    @Transactional
                    public void process(String id) {
                    }
                }

                class MiddleService extends BaseService {
                }

                class ChildService extends MiddleService {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void detectsCrossSourceRootTransactionalSelfInvocation() throws Exception {
        Path moduleRoot = tempDir.resolve("module-a/src/main/java");
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Files.createDirectories(moduleRoot.resolve("demo"));
        Files.createDirectories(rootApp.resolve("demo"));
        Files.writeString(moduleRoot.resolve("demo/BaseService.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                class BaseService {

                    @Transactional
                    public void process(String id) {
                    }
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                class ChildService extends BaseService {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, List.of(rootApp, moduleRoot));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void detectsInterfaceDefaultTransactionalSelfInvocation() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("InterfaceDefaultSelfInvocation.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                interface TxDefaults {

                    @Transactional
                    default void process() {
                    }
                }

                class DefaultTxService implements TxDefaults {

                    public void outer() {
                        process();
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void detectsGenericInterfaceDefaultTransactionalSelfInvocation() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("GenericInterfaceDefaultSelfInvocation.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                interface TxDefaults<T> {

                    @Transactional
                    default void process(T value) {
                    }
                }

                class DefaultTxService implements TxDefaults<String> {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void detectsFinalClassInterfaceDefaultTransactionalSelfInvocation() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("FinalInterfaceDefaultSelfInvocation.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                interface TxDefaults {

                    @Transactional
                    default void process() {
                    }
                }

                final class FinalTxService implements TxDefaults {

                    public void outer() {
                        process();
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void flagsMethodReferencesForTransactionalSelfInvocation() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("TransactionalSelfInvocationMethodRef.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                class TransactionalSelfInvocationMethodRef {

                    public void outer() {
                        Runnable task = this::inner;
                        task.run();
                    }

                    @Transactional
                    public void inner() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("inner"));
    }

    @Test
    void resolvesSamePackageEvenWhenSimpleNameIsAmbiguousAcrossPackages() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Path moduleB = tempDir.resolve("module-b/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("demo"));
        Files.createDirectories(moduleB.resolve("other"));
        Files.writeString(moduleA.resolve("demo/BaseService.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                class BaseService {

                    @Transactional
                    public void process(String id) {
                    }
                }
                """);
        Files.writeString(moduleB.resolve("other/BaseService.java"), """
                package other;

                import org.springframework.transaction.annotation.Transactional;

                class BaseService {

                    @Transactional
                    public void process(String id) {
                    }
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                class ChildService extends BaseService {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, List.of(rootApp, moduleA, moduleB));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void resolvesWildcardImportInheritanceAcrossPackages() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("other"));
        Files.writeString(moduleA.resolve("other/BaseService.java"), """
                package other;

                import org.springframework.transaction.annotation.Transactional;

                class BaseService {

                    @Transactional
                    public void process(String id) {
                    }
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                import other.*;

                class ChildService extends BaseService {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, List.of(rootApp, moduleA));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void resolvesNestedTypeInheritanceAcrossPackages() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("other"));
        Files.writeString(moduleA.resolve("other/Outer.java"), """
                package other;

                import org.springframework.transaction.annotation.Transactional;

                class Outer {

                    static class BaseService {

                        @Transactional
                        public void process(String id) {
                        }
                    }
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                import other.Outer;

                class ChildService extends Outer.BaseService {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, List.of(rootApp, moduleA));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void resolvesSamePackageNestedTypeInheritanceWithoutImport() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("demo"));
        Files.writeString(moduleA.resolve("demo/Outer.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                class Outer {

                    static class BaseService {

                        @Transactional
                        public void process(String id) {
                        }
                    }
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                class ChildService extends Outer.BaseService {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, List.of(rootApp, moduleA));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void resolvesExplicitNestedTypeImportInheritanceAcrossPackages() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("other"));
        Files.writeString(moduleA.resolve("other/Outer.java"), """
                package other;

                import org.springframework.transaction.annotation.Transactional;

                class Outer {

                    static class BaseService {

                        @Transactional
                        public void process(String id) {
                        }
                    }
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                import other.Outer.BaseService;

                class ChildService extends BaseService {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, List.of(rootApp, moduleA));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("process"));
    }

    @Test
    void doesNotResolveAmbiguousWildcardNestedTypes() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Path moduleB = tempDir.resolve("module-b/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("other"));
        Files.createDirectories(moduleB.resolve("another"));
        Files.writeString(moduleA.resolve("other/Outer.java"), """
                package other;

                import org.springframework.transaction.annotation.Transactional;

                class Outer {

                    static class BaseService {

                        @Transactional
                        public void process(String id) {
                        }
                    }
                }
                """);
        Files.writeString(moduleB.resolve("another/Outer.java"), """
                package another;

                import org.springframework.transaction.annotation.Transactional;

                class Outer {

                    static class BaseService {

                        @Transactional
                        public void process(String id) {
                        }
                    }
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                import other.*;
                import another.*;

                class ChildService extends Outer.BaseService {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, List.of(rootApp, moduleA, moduleB));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(0, selfInvocationIssues.size());
    }

    @Test
    void doesNotResolveCrossPackageInheritanceWithoutExplicitTypeMatch() throws Exception {
        Path rootApp = tempDir.resolve("root-app/src/main/java");
        Path moduleA = tempDir.resolve("module-a/src/main/java");
        Files.createDirectories(rootApp.resolve("demo"));
        Files.createDirectories(moduleA.resolve("other"));
        Files.writeString(moduleA.resolve("other/BaseService.java"), """
                package other;

                import org.springframework.transaction.annotation.Transactional;

                class BaseService {

                    @Transactional
                    public void process(String id) {
                    }
                }
                """);
        Files.writeString(rootApp.resolve("demo/ChildService.java"), """
                package demo;

                class ChildService extends BaseService {

                    public void outer() {
                        process("id");
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, List.of(rootApp, moduleA));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(0, selfInvocationIssues.size());
    }

    @Test
    void ignoresProxyInjectionPatternsButFlagsExplicitSelfCalls() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("TransactionalSelfInvocationProxy.java"), """
                package demo;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.context.ApplicationContext;
                import org.springframework.transaction.annotation.Transactional;

                class TransactionalSelfInvocationProxy {

                    @Autowired
                    private TransactionalSelfInvocationProxy self;

                    @Autowired
                    private ApplicationContext context;

                    public void outer() {
                        self.inner();
                        context.getBean(TransactionalSelfInvocationProxy.class).inner();
                        this.inner();
                    }

                    @Transactional
                    public void inner() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();

        assertEquals(1, selfInvocationIssues.size());
        assertTrue(selfInvocationIssues.get(0).message().contains("inner"));
    }

    @Test
    void doesNotFlagFinalTransactionalSelfCalls() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("FinalTransactionalSelfInvocation.java"), """
                package demo;

                import org.springframework.transaction.annotation.Transactional;

                class FinalTransactionalSelfInvocation {

                    public void outer() {
                        finalInner();
                    }

                    @Transactional
                    public final void finalInner() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> selfInvocationIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_SELF_INVOCATION"))
                .toList();
        List<LintIssue> finalMethodIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_TX_FINAL_METHOD"))
                .toList();

        assertEquals(0, selfInvocationIssues.size());
        assertEquals(1, finalMethodIssues.size());
    }

    @Test
    void recognizesComposedTransactionalControllerAndSecurityAnnotations() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("MetaAnnotations.java"), """
                package demo;

                import org.springframework.core.annotation.AliasFor;
                import org.springframework.scheduling.annotation.Async;
                import org.springframework.security.access.prepost.PreAuthorize;
                import org.springframework.transaction.annotation.Propagation;
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                @RestController
                @interface ApiController {
                }

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @GetMapping
                @interface ApiGet {
                }

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @PreAuthorize("hasRole('ADMIN')")
                @interface ProtectedEndpoint {
                }

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @Transactional
                @interface RequiresNewTx {

                    @AliasFor(annotation = Transactional.class, attribute = "propagation")
                    Propagation propagation() default Propagation.REQUIRES_NEW;
                }

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @org.springframework.cache.annotation.Cacheable(cacheNames = "demo")
                @interface KeyedCacheable {

                    @AliasFor(annotation = org.springframework.cache.annotation.Cacheable.class, attribute = "key")
                    String key() default "";
                }

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @org.springframework.cache.annotation.Cacheable(cacheNames = "demo")
                @interface GeneratorCacheable {

                    @AliasFor(annotation = org.springframework.cache.annotation.Cacheable.class, attribute = "keyGenerator")
                    String keyGenerator() default "";
                }
                """);
        Files.writeString(sourceDirectory.resolve("ComposedUsage.java"), """
                package demo;

                import org.springframework.context.annotation.Profile;

                @ApiController
                @Profile("demo")
                class DemoController {

                    @ApiGet
                    @ProtectedEndpoint
                    public String secureOpen() {
                        return "ok";
                    }
                }

                class TransactionalService {

                    public void outer() {
                        this.inner();
                    }

                    @RequiresNewTx(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
                    private void inner() {
                    }

                    @RequiresNewTx(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
                    public final void finalTransactional() {
                    }

                    @KeyedCacheable(key = "#id")
                    public String cacheLoad(String id) {
                        return id;
                    }

                    @GeneratorCacheable(keyGenerator = "demoKeyGenerator")
                    public String cacheLoadWithGenerator(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertTrue(issueIds.contains("SPRING_PROFILE_CONTROLLER"));
        assertTrue(issueIds.contains("SPRING_TX_SELF_INVOCATION"));
        assertTrue(issueIds.contains("SPRING_TX_PRIVATE_METHOD"));
        assertTrue(issueIds.contains("SPRING_TX_FINAL_METHOD"));
        assertTrue(issueIds.contains("SPRING_TX_HIGH_RISK_PROPAGATION"));
        assertFalse(issueIds.contains("SPRING_ENDPOINT_SECURITY"));
        assertFalse(issueIds.contains("SPRING_CACHEABLE_KEY"));
    }

    @Test
    void recognizesClassLevelComposedSecurityAnnotations() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ClassLevelSecurityMeta.java"), """
                package demo;

                import org.springframework.security.access.prepost.PreAuthorize;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                @RestController
                @PreAuthorize("hasRole('ADMIN')")
                @interface SecureController {
                }

                @SecureController
                class SecureEndpointController {

                    @GetMapping("/secure")
                    public String secure() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_ENDPOINT_SECURITY"));
    }

    @Test
    void recognizesAdditionalMethodSecurityAnnotationsAndAliasForwarding() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("MethodLevelSecurityMeta.java"), """
                package demo;

                import org.springframework.core.annotation.AliasFor;
                import org.springframework.security.access.prepost.PreAuthorize;
                import org.springframework.security.access.prepost.PreFilter;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @PreAuthorize("hasRole('ADMIN')")
                @interface SecurePolicy {

                    @AliasFor(annotation = PreAuthorize.class, attribute = "value")
                    String value() default "";
                }

                @RestController
                class SecureController {

                    @GetMapping("/filtered")
                    @PreFilter("filterObject != null")
                    public String filtered() {
                        return "ok";
                    }

                    @GetMapping("/alias")
                    @SecurePolicy("hasRole('ADMIN')")
                    public String aliasSecured() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_ENDPOINT_SECURITY"));
    }

    @Test
    void honorsCentralizedSecurityConfiguration() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("PublicController.java"), """
                package demo;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class PublicController {

                    @GetMapping("/open")
                    public String open() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport defaultReport = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> defaultIssueIds = defaultReport.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());
        assertTrue(defaultIssueIds.contains("SPRING_ENDPOINT_SECURITY"));

        LintOptions options = LintOptions.defaults().withAssumeCentralizedSecurity(true);
        LintReport configuredReport = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        Set<String> configuredIssueIds = configuredReport.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(configuredIssueIds.contains("SPRING_ENDPOINT_SECURITY"));
    }

    @Test
    void honorsCustomSecurityAnnotations() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("CustomSecurity.java"), """
                package demo;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @interface InternalEndpoint {
                }

                @RestController
                class InternalController {

                    @GetMapping("/internal")
                    @InternalEndpoint
                    public String internal() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintOptions options = LintOptions.defaults().withCustomSecurityAnnotations(Set.of("InternalEndpoint"));
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_ENDPOINT_SECURITY"));
    }

    @Test
    void autoDetectsSecurityFilterChainForCentralizedSecurity() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("SecurityConfig.java"), """
                package demo;

                import org.springframework.context.annotation.Bean;
                import org.springframework.security.web.SecurityFilterChain;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                class SecurityConfig {

                    @Bean
                    SecurityFilterChain filterChain() {
                        return null;
                    }
                }

                @RestController
                class PublicController {

                    @GetMapping("/open")
                    public String open() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport defaultReport = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        assertTrue(defaultReport.issues().stream().anyMatch(issue -> issue.ruleId().equals("SPRING_ENDPOINT_SECURITY")));

        LintOptions options = LintOptions.defaults().withAutoDetectCentralizedSecurity(true);
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        assertFalse(report.issues().stream().anyMatch(issue -> issue.ruleId().equals("SPRING_ENDPOINT_SECURITY")));
    }

    @Test
    void autoDetectsSecurityWebFilterChainForCentralizedSecurity() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ReactiveSecurityConfig.java"), """
                package demo;

                import org.springframework.context.annotation.Bean;
                import org.springframework.security.web.server.SecurityWebFilterChain;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                class ReactiveSecurityConfig {

                    @Bean
                    SecurityWebFilterChain filterChain() {
                        return null;
                    }
                }

                @RestController
                class PublicController {

                    @GetMapping("/open")
                    public String open() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport defaultReport = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        assertTrue(defaultReport.issues().stream().anyMatch(issue -> issue.ruleId().equals("SPRING_ENDPOINT_SECURITY")));

        LintOptions options = LintOptions.defaults().withAutoDetectCentralizedSecurity(true);
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        assertFalse(report.issues().stream().anyMatch(issue -> issue.ruleId().equals("SPRING_ENDPOINT_SECURITY")));
    }

    @Test
    void honorsInheritedSecurityAnnotations() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("InheritedSecurity.java"), """
                package demo;

                import org.springframework.security.access.prepost.PreAuthorize;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                interface SecuredApi {

                    @PreAuthorize("hasRole('ADMIN')")
                    String secured();
                }

                @RestController
                class InterfaceBackedController implements SecuredApi {

                    @Override
                    @GetMapping("/secured")
                    public String secured() {
                        return "ok";
                    }
                }

                @PreAuthorize("hasRole('ADMIN')")
                abstract class SecuredBaseController {
                }

                @RestController
                class BaseClassController extends SecuredBaseController {

                    @GetMapping("/base")
                    public String base() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> issues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_ENDPOINT_SECURITY"))
                .toList();

        assertTrue(issues.isEmpty());
    }

    @Test
    void resolvesAmbiguousSecurityAnnotationsByImports() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceDirectory.resolve("a"));
        Files.createDirectories(sourceDirectory.resolve("b"));
        Files.createDirectories(sourceDirectory.resolve("demo"));
        Files.writeString(sourceDirectory.resolve("a/Secure.java"), """
                package a;

                import org.springframework.security.access.prepost.PreAuthorize;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @PreAuthorize("hasRole('ADMIN')")
                public @interface Secure {
                }
                """);
        Files.writeString(sourceDirectory.resolve("b/Secure.java"), """
                package b;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Secure {
                }
                """);
        Files.writeString(sourceDirectory.resolve("demo/SecureControllerA.java"), """
                package demo;

                import a.Secure;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class SecureControllerA {

                    @GetMapping("/a")
                    @Secure
                    public String secure() {
                        return "ok";
                    }
                }
                """);
        Files.writeString(sourceDirectory.resolve("demo/SecureControllerB.java"), """
                package demo;

                import b.Secure;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class SecureControllerB {

                    @GetMapping("/b")
                    @Secure
                    public String open() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> issues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_ENDPOINT_SECURITY"))
                .toList();

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).file().toString().contains("SecureControllerB"));
    }

    @Test
    void flagsComposedCacheableWithoutExplicitKeyStrategy() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ComposedCacheDefaults.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @Cacheable(cacheNames = "demo")
                @interface BasicCacheable {
                }

                class CacheDefaults {

                    @BasicCacheable
                    public String lookup(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> cacheIssues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_CACHEABLE_KEY"))
                .toList();

        assertEquals(1, cacheIssues.size());
        assertTrue(cacheIssues.get(0).message().contains("lookup"));
    }

    @Test
    void treatsWildcardSecurityAnnotationsAsAmbiguous() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceDirectory.resolve("a"));
        Files.createDirectories(sourceDirectory.resolve("b"));
        Files.createDirectories(sourceDirectory.resolve("demo"));
        Files.writeString(sourceDirectory.resolve("a/Secure.java"), """
                package a;

                import org.springframework.security.access.prepost.PreAuthorize;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @PreAuthorize("hasRole('ADMIN')")
                public @interface Secure {
                }
                """);
        Files.writeString(sourceDirectory.resolve("b/Secure.java"), """
                package b;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Secure {
                }
                """);
        Files.writeString(sourceDirectory.resolve("demo/WildcardController.java"), """
                package demo;

                import a.*;
                import b.*;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class WildcardController {

                    @GetMapping("/wildcard")
                    @Secure
                    public String wildcard() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> issues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_ENDPOINT_SECURITY"))
                .toList();

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).file().toString().contains("WildcardController"));
    }

    @Test
    void detectsScheduledMethodRisks() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ScheduledService.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Scheduled;

                class ScheduledService {

                    @Scheduled
                    public void missingTrigger() {
                    }

                    @Scheduled(fixedRate = 1000, cron = "0 * * * * *")
                    public void conflictingTrigger() {
                    }

                    @Scheduled(fixedDelay = 1000)
                    public void withArgument(String input) {
                    }

                    @Scheduled(fixedRate = 1000)
                    public String returningValue() {
                        return "ok";
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertTrue(issueIds.contains("SPRING_SCHEDULED_TRIGGER_CONFIGURATION"));
        assertTrue(issueIds.contains("SPRING_SCHEDULED_METHOD_PARAMETERS"));
        assertTrue(issueIds.contains("SPRING_SCHEDULED_RETURN_VALUE"));
    }

    @Test
    void treatsScheduledPlaceholdersAsConfigured() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ScheduledPlaceholders.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Scheduled;

                class ScheduledPlaceholders {

                    @Scheduled(cron = "${demo.cron}")
                    public void cronPlaceholder() {
                    }

                    @Scheduled(cron = "${demo.cron}", fixedRateString = "${demo.rate}")
                    public void mixedPlaceholders() {
                    }

                    @Scheduled
                    public void missingTrigger() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        List<LintIssue> issues = report.issues().stream()
                .filter(issue -> issue.ruleId().equals("SPRING_SCHEDULED_TRIGGER_CONFIGURATION"))
                .toList();

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).message().contains("missingTrigger"));
    }

    @Test
    void honorsComposedScheduledTriggerConfiguration() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ComposedScheduled.java"), """
                package demo;

                import org.springframework.core.annotation.AliasFor;
                import org.springframework.scheduling.annotation.Scheduled;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @Scheduled
                @interface CronScheduled {

                    @AliasFor(annotation = Scheduled.class, attribute = "cron")
                    String cron() default "";
                }

                class CronService {

                    @CronScheduled(cron = "0 * * * * *")
                    public void cron() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_SCHEDULED_TRIGGER_CONFIGURATION"));
    }

    @Test
    void honorsComposedScheduledDelayPlaceholder() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ComposedDelayScheduled.java"), """
                package demo;

                import org.springframework.core.annotation.AliasFor;
                import org.springframework.scheduling.annotation.Scheduled;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @Scheduled
                @interface DelayScheduled {

                    @AliasFor(annotation = Scheduled.class, attribute = "fixedDelayString")
                    String delay() default "";
                }

                class DelayService {

                    @DelayScheduled(delay = "${demo.delay}")
                    public void delayed() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_SCHEDULED_TRIGGER_CONFIGURATION"));
    }

    @Test
    void flagsRepeatedScheduledTriggersWithComposedConfiguration() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("RepeatedScheduled.java"), """
                package demo;

                import org.springframework.core.annotation.AliasFor;
                import org.springframework.scheduling.annotation.Scheduled;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @Scheduled
                @interface CronScheduled {

                    @AliasFor(annotation = Scheduled.class, attribute = "cron")
                    String cron() default "";
                }

                class RepeatedService {

                    @CronScheduled(cron = "0 * * * * *")
                    @Scheduled(fixedRate = 1000)
                    public void mixed() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertTrue(issueIds.contains("SPRING_SCHEDULED_TRIGGER_CONFIGURATION"));
    }

    @Test
    void honorsComposedCacheNamesViaAliasFor() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ComposedCacheNames.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.core.annotation.AliasFor;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @Cacheable
                @interface LocalCache {

                    @AliasFor(annotation = Cacheable.class, attribute = "cacheNames")
                    String[] cacheNames() default {};
                }

                class CacheService {

                    @LocalCache(cacheNames = "safe")
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintOptions options = LintOptions.defaults().withCacheDefaultKeyCacheNames(Set.of("safe"));
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_CACHEABLE_KEY"));
    }

    @Test
    void honorsComposedCacheKeyGeneratorAlias() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ComposedCacheKeyGenerator.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.core.annotation.AliasFor;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                @Cacheable
                @interface KeyedCacheable {

                    @AliasFor(annotation = Cacheable.class, attribute = "keyGenerator")
                    String keyGenerator() default "";
                }

                class CacheService {

                    @KeyedCacheable(keyGenerator = "demoKeyGenerator")
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_CACHEABLE_KEY"));
    }

    @Test
    void detectsScheduledAsyncAndTransactionalBoundaries() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ScheduledBoundaryService.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;
                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.transaction.annotation.Transactional;

                @Transactional
                class ScheduledBoundaryService {

                    @Scheduled(fixedRate = 1000)
                    @Async
                    public void asyncScheduled() {
                    }

                    @Scheduled(fixedDelay = 1000)
                    public void transactionalScheduled() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertTrue(issueIds.contains("SPRING_SCHEDULED_ASYNC_BOUNDARY"));
        assertTrue(issueIds.contains("SPRING_SCHEDULED_TRANSACTIONAL_BOUNDARY"));
    }

    @Test
    void allowsDefaultCacheKeyForConfiguredCaches() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("CacheAllowlist.java"), """
                package demo;

                import org.springframework.cache.annotation.Cacheable;

                class CacheAllowlist {

                    @Cacheable(cacheNames = "safe")
                    public String load(String id) {
                        return id;
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintOptions options = LintOptions.defaults().withCacheDefaultKeyCacheNames(Set.of("safe"));
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"), options).report();
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertFalse(issueIds.contains("SPRING_CACHEABLE_KEY"));
    }

    @Test
    void detectsRepeatedSchedulesAndNonPositiveIntervals() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("ScheduledAdvancedService.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Scheduled;

                class ScheduledAdvancedService {

                    @Scheduled(fixedRate = 1000)
                    @Scheduled(cron = "0 * * * * *")
                    public void repeatedSchedule() {
                    }

                    @Scheduled(fixedDelay = 0)
                    public void zeroDelay() {
                    }

                    @Scheduled(initialDelayString = "-5")
                    public void negativeInitialDelay() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertTrue(issueIds.contains("SPRING_SCHEDULED_REPEATED_TRIGGER"));
        assertTrue(issueIds.contains("SPRING_SCHEDULED_NON_POSITIVE_INTERVAL"));
    }

    @Test
    void detectsLifecycleAsyncAndTransactionalBoundaries() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("LifecycleService.java"), """
                package demo;

                import jakarta.annotation.PostConstruct;
                import org.springframework.beans.factory.InitializingBean;
                import org.springframework.scheduling.annotation.Async;
                import org.springframework.transaction.annotation.Transactional;

                @Transactional
                class LifecycleService implements InitializingBean {

                    @PostConstruct
                    @Async
                    public void initializeAsync() {
                    }

                    @Override
                    public void afterPropertiesSet() {
                    }
                }
                """);

        ProjectLinter linter = new ProjectLinter(SpringBootRuleSet.defaultRules());
        LintReport report = linter.analyze(tempDir, tempDir.resolve("src/main/java"));
        Set<String> issueIds = report.issues().stream().map(LintIssue::ruleId).collect(Collectors.toSet());

        assertTrue(issueIds.contains("SPRING_LIFECYCLE_ASYNC_BOUNDARY"));
        assertTrue(issueIds.contains("SPRING_LIFECYCLE_TRANSACTIONAL_BOUNDARY"));
    }

}
