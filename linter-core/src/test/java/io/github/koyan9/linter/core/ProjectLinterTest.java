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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        assertEquals(13, report.rules().size());
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
        assertEquals(13, report.rules().size());
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
}
