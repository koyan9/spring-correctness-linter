/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrectnessLintMojoTest {

    @TempDir
    Path tempDir;

    @Test
    void writesReportsAndBaselineToConfiguredLocations() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("build/custom-reports");
        Path baselineFile = tempDir.resolve("custom-baseline.txt");

        CorrectnessLintMojo mojo = configuredMojo(sourceDirectory, reportsDirectory, baselineFile);
        setField(mojo, "applyBaseline", false);
        setField(mojo, "writeBaseline", true);

        mojo.execute();

        assertTrue(Files.exists(reportsDirectory.resolve("lint-report.json")));
        assertTrue(Files.exists(reportsDirectory.resolve("lint-report.html")));
        assertTrue(Files.exists(reportsDirectory.resolve("lint-report.sarif.json")));
        assertTrue(Files.exists(reportsDirectory.resolve("baseline-diff.html")));
        assertTrue(Files.exists(reportsDirectory.resolve("rules-reference.md")));
        assertTrue(Files.exists(baselineFile));
    }

    @Test
    void failsBuildWhenConfiguredSeverityThresholdMatches() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "failOnSeverity", "WARNING");

        assertThrows(MojoFailureException.class, mojo::execute);
    }

    @Test
    void writesParseProblemSummaryIntoJsonReport() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("Broken.java"), """
                package demo;

                class Broken {
                    public void run( {
                """);
        Path reportsDirectory = tempDir.resolve("target/reports");

        CorrectnessLintMojo mojo = configuredMojo(
                tempDir.resolve("src/main/java"),
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"parseProblemFileCount\": 1"));
        assertTrue(json.contains("\"runtimeMetrics\""));
        assertTrue(json.contains("Broken.java"));
    }

    @Test
    void disablesConfiguredRules() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-disabled-rule");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "disabledRules", "SPRING_ASYNC_VOID");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertTrue(json.contains("\"disabledRuleIds\""));
        assertTrue(json.contains("SPRING_ASYNC_VOID"));
        assertFalse(json.contains("\"ruleId\": \"SPRING_ASYNC_VOID\""));
    }

    @Test
    void enablesConfiguredRuleDomains() throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("MixedService.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;
                import org.springframework.transaction.annotation.Transactional;

                class MixedService {

                    @Async
                    public void runAsync() {
                    }

                    @Transactional
                    private void runTransactional() {
                    }
                }
                """);

        Path reportsDirectory = tempDir.resolve("target/reports-enabled-domains");
        CorrectnessLintMojo mojo = configuredMojo(
                tempDir.resolve("src/main/java"),
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "enabledRuleDomains", "transaction");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"ruleDomainSelection\""));
        assertTrue(json.contains("\"enabledDomains\""));
        assertTrue(json.contains("\"TRANSACTION\""));
        assertTrue(json.contains("SPRING_TX_PRIVATE_METHOD"));
        assertFalse(json.contains("SPRING_ASYNC_VOID"));
    }

    @Test
    void rejectsInvalidRuleDomainValues() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-invalid-domain"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "enabledRuleDomains", "not-a-domain");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(exception.getMessage().contains("Invalid rule domain value"));
    }

    @Test
    void appliesConfiguredSeverityOverrides() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-overrides"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "severityOverrides", "SPRING_ASYNC_VOID=ERROR");
        setField(mojo, "failOnSeverity", "ERROR");

        assertThrows(MojoFailureException.class, mojo::execute);
    }

    @Test
    void reusesIncrementalCacheAcrossMojoExecutions() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-cached");
        Path cacheFile = tempDir.resolve("target/analysis-cache.txt");

        CorrectnessLintMojo firstRun = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(firstRun, "applyBaseline", false);
        setField(firstRun, "useIncrementalCache", true);
        setField(firstRun, "cacheFile", cacheFile.toFile());
        setField(firstRun, "formats", new LinkedHashSet<>(Set.of("json")));
        firstRun.execute();

        CorrectnessLintMojo secondRun = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(secondRun, "applyBaseline", false);
        setField(secondRun, "useIncrementalCache", true);
        setField(secondRun, "cacheFile", cacheFile.toFile());
        setField(secondRun, "formats", new LinkedHashSet<>(Set.of("json")));
        secondRun.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"cachedFileCount\": 1"));
        assertTrue(json.contains("\"cacheScope\": \"shared-file\""));
    }

    @Test
    void scansReactorModulesFromExecutionRoot() throws Exception {
        Path rootSourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class RootAsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path moduleSourceDirectory = tempDir.resolve("module-a/src/main/java/demo");
        Files.createDirectories(moduleSourceDirectory);
        Files.writeString(moduleSourceDirectory.resolve("ModuleAsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class ModuleAsyncOnly {

                    @Async
                    private void runAsync() {
                    }
                }
                """);

        MavenProject rootProject = new MavenProject();
        rootProject.setArtifactId("root-app");
        rootProject.setFile(tempDir.resolve("pom.xml").toFile());
        rootProject.setExecutionRoot(true);
        rootProject.getCompileSourceRoots().add(rootSourceDirectory.toString());

        MavenProject moduleProject = new MavenProject();
        moduleProject.setArtifactId("module-a");
        moduleProject.setFile(tempDir.resolve("module-a/pom.xml").toFile());
        moduleProject.setExecutionRoot(false);
        moduleProject.getCompileSourceRoots().add(tempDir.resolve("module-a/src/main/java").toString());

        CorrectnessLintMojo mojo = configuredMojo(
                rootSourceDirectory,
                tempDir.resolve("target/reactor-reports"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "scanReactorModules", true);
        setField(mojo, "project", rootProject);
        setField(mojo, "reactorProjects", List.of(rootProject, moduleProject));
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(tempDir.resolve("target/reactor-reports/lint-report.json"));
        assertTrue(json.contains("\"sourceDirectoryCount\": 2"));
        assertTrue(json.contains("\"moduleId\": \"root-app\""));
        assertTrue(json.contains("\"moduleId\": \"module-a\""));
        assertTrue(json.contains("\"module\": \"root-app\""));
        assertTrue(json.contains("\"module\": \"module-a\""));
        assertTrue(json.contains("SPRING_ASYNC_VOID"));
        assertTrue(json.contains("SPRING_ASYNC_PRIVATE_METHOD"));
    }

    @Test
    void writesPerModuleBaselineAndCacheFilesWhenEnabled() throws Exception {
        Path rootSourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class RootAsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path moduleSourceDirectory = tempDir.resolve("module-a/src/main/java/demo");
        Files.createDirectories(moduleSourceDirectory);
        Files.writeString(moduleSourceDirectory.resolve("ModuleAsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class ModuleAsyncOnly {

                    @Async
                    private void runAsync() {
                    }
                }
                """);

        MavenProject rootProject = new MavenProject();
        rootProject.setArtifactId("root-app");
        rootProject.setFile(tempDir.resolve("pom.xml").toFile());
        rootProject.setExecutionRoot(true);
        rootProject.getCompileSourceRoots().add(rootSourceDirectory.toString());

        MavenProject moduleProject = new MavenProject();
        moduleProject.setArtifactId("module-a");
        moduleProject.setFile(tempDir.resolve("module-a/pom.xml").toFile());
        moduleProject.setExecutionRoot(false);
        moduleProject.getCompileSourceRoots().add(tempDir.resolve("module-a/src/main/java").toString());

        Path baselinePath = tempDir.resolve("spring-correctness-linter-baseline.txt");
        Path cachePath = tempDir.resolve("target/analysis-cache.txt");

        CorrectnessLintMojo firstRun = configuredMojo(
                rootSourceDirectory,
                tempDir.resolve("target/reactor-split-reports"),
                baselinePath
        );
        setField(firstRun, "applyBaseline", false);
        setField(firstRun, "writeBaseline", true);
        setField(firstRun, "scanReactorModules", true);
        setField(firstRun, "splitBaselineByModule", true);
        setField(firstRun, "splitCacheByModule", true);
        setField(firstRun, "useIncrementalCache", true);
        setField(firstRun, "cacheFile", cachePath.toFile());
        setField(firstRun, "project", rootProject);
        setField(firstRun, "reactorProjects", List.of(rootProject, moduleProject));
        setField(firstRun, "formats", new LinkedHashSet<>(Set.of("json")));
        firstRun.execute();

        assertTrue(Files.exists(tempDir.resolve("modules/root-app/spring-correctness-linter-baseline.txt")));
        assertTrue(Files.exists(tempDir.resolve("modules/module-a/spring-correctness-linter-baseline.txt")));
        assertTrue(Files.exists(tempDir.resolve("target/modules/root-app/analysis-cache.txt")));
        assertTrue(Files.exists(tempDir.resolve("target/modules/module-a/analysis-cache.txt")));

        CorrectnessLintMojo secondRun = configuredMojo(
                rootSourceDirectory,
                tempDir.resolve("target/reactor-split-reports"),
                baselinePath
        );
        setField(secondRun, "applyBaseline", false);
        setField(secondRun, "scanReactorModules", true);
        setField(secondRun, "splitBaselineByModule", true);
        setField(secondRun, "splitCacheByModule", true);
        setField(secondRun, "useIncrementalCache", true);
        setField(secondRun, "cacheFile", cachePath.toFile());
        setField(secondRun, "project", rootProject);
        setField(secondRun, "reactorProjects", List.of(rootProject, moduleProject));
        setField(secondRun, "formats", new LinkedHashSet<>(Set.of("json")));
        secondRun.execute();

        String json = Files.readString(tempDir.resolve("target/reactor-split-reports/lint-report.json"));
        assertTrue(json.contains("\"cachedFileCount\": 2"));
    }


    @Test
    void honorsCentralizedSecurityConfiguration() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class OpenController {

                    @GetMapping("/open")
                    public String open() {
                        return "ok";
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-centralized-security");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "assumeCentralizedSecurity", true);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertFalse(json.contains("\"ruleId\": \"SPRING_ENDPOINT_SECURITY\""));
    }

    @Test
    void honorsConfiguredSecurityAnnotations() throws Exception {
        Path sourceDirectory = writeSource("""
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
        Path reportsDirectory = tempDir.resolve("target/reports-custom-security");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "securityAnnotations", "InternalEndpoint");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertFalse(json.contains("\"ruleId\": \"SPRING_ENDPOINT_SECURITY\""));
    }

    @Test
    void honorsCacheDefaultKeyAllowlist() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.cache.annotation.Cacheable;

                class CacheService {

                    @Cacheable(cacheNames = "safe")
                    public String load(String id) {
                        return id;
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-cache-allowlist");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "cacheDefaultKeyCacheNames", "safe");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertFalse(json.contains("\"ruleId\": \"SPRING_CACHEABLE_KEY\""));
    }


    @Test
    void normalizesSecurityAnnotationNames() throws Exception {
        Path sourceDirectory = writeSource("""
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
        Path reportsDirectory = tempDir.resolve("target/reports-security-normalized");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "securityAnnotations", "@demo.InternalEndpoint");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertFalse(json.contains("\"ruleId\": \"SPRING_ENDPOINT_SECURITY\""));
    }

    @Test
    void allowsWildcardCacheDefaultKeys() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.cache.annotation.Cacheable;

                class CacheService {

                    @Cacheable(cacheNames = "safe")
                    public String load(String id) {
                        return id;
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-cache-wildcard");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "cacheDefaultKeyCacheNames", "*");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertFalse(json.contains("\"ruleId\": \"SPRING_CACHEABLE_KEY\""));
    }

    @Test
    void writesRuleGovernanceSnapshot() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-governance");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        Path governanceFile = reportsDirectory.resolve("rules-governance.json");
        assertTrue(Files.exists(governanceFile));
        String json = Files.readString(governanceFile);
        assertTrue(json.contains("\"ruleCount\""));
        assertTrue(json.contains("\"rules\""));
    }

    @Test
    void writesLightweightJsonReportWhenEnabled() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-light");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "lightweightReports", true);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"summary\""));
        assertTrue(json.contains("\"ruleDomainSelection\""));
        assertFalse(json.contains("\"issues\""));
        assertFalse(json.contains("\"runtimeMetrics\""));
    }

    @Test
    void acceptsParallelAnalysisConfiguration() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-parallel");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "parallelFileAnalysis", true);
        setField(mojo, "fileAnalysisParallelism", 2);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 1"));
        assertTrue(json.contains("\"runtimeMetrics\""));
    }

    @Test
    void rejectsNegativeFileAnalysisParallelism() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class AsyncOnly {
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-invalid-parallelism"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "fileAnalysisParallelism", -1);

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(containsMessage(exception, "fileAnalysisParallelism"));
    }

    @Test
    void includesModuleSpecificSourceDirectories() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class BaseService {
                }
                """);
        Path extraRoot = tempDir.resolve("custom-src/demo");
        Files.createDirectories(extraRoot);
        Files.writeString(extraRoot.resolve("ExtraService.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class ExtraService {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-module-extra");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "moduleSourceDirectories", "empty-project=custom-src");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 1"));
        assertTrue(json.contains("\"ruleId\": \"SPRING_ASYNC_VOID\""));
    }

    @Test
    void autoDetectsProjectWideKeyGenerator() throws Exception {
        Path sourceDirectory = writeSource("""
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
        Path reportsDirectory = tempDir.resolve("target/reports-project-key-generator");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "autoDetectProjectWideKeyGenerator", true);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertFalse(json.contains("\"ruleId\": \"SPRING_CACHEABLE_KEY\""));
    }

    @Test
    void autoDetectsCachingConfigurerKeyGenerator() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.cache.annotation.CachingConfigurerSupport;
                import org.springframework.cache.interceptor.KeyGenerator;

                class ProjectKeyGeneratorConfig extends CachingConfigurerSupport {

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
        Path reportsDirectory = tempDir.resolve("target/reports-caching-configurer");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "autoDetectProjectWideKeyGenerator", true);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertFalse(json.contains("\"ruleId\": \"SPRING_CACHEABLE_KEY\""));
    }

    private Path writeSource(String content) throws Exception {
        Path sourceDirectory = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("AsyncOnly.java"), content);
        return tempDir.resolve("src/main/java");
    }

    private CorrectnessLintMojo configuredMojo(Path sourceDirectory, Path reportsDirectory, Path baselineFile) throws Exception {
        CorrectnessLintMojo mojo = new CorrectnessLintMojo();
        setField(mojo, "projectBaseDir", tempDir.toFile());
        setField(mojo, "sourceDirectory", sourceDirectory.toFile());
        setField(mojo, "reportDirectory", reportsDirectory.toFile());
        setField(mojo, "baselineFile", baselineFile.toFile());
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json", "html", "sarif")));
        setField(mojo, "honorInlineSuppressions", true);
        setField(mojo, "applyBaseline", true);
        setField(mojo, "writeBaselineDiff", true);
        setField(mojo, "writeRuleDocs", true);
        setField(mojo, "ruleDocsFileName", "rules-reference.md");
        setField(mojo, "lightweightReports", false);
        setField(mojo, "failOnError", false);
        setField(mojo, "useIncrementalCache", false);
        setField(mojo, "parallelFileAnalysis", true);
        setField(mojo, "fileAnalysisParallelism", 0);
        setField(mojo, "cacheFile", tempDir.resolve("analysis-cache.txt").toFile());
        setField(mojo, "assumeCentralizedSecurity", false);
        setField(mojo, "autoDetectCentralizedSecurity", false);
        setField(mojo, "autoDetectProjectWideKeyGenerator", false);
        MavenProject project = new MavenProject();
        project.setFile(tempDir.resolve("pom.xml").toFile());
        project.setExecutionRoot(true);
        project.getCompileSourceRoots().add(sourceDirectory.toString());
        setField(mojo, "project", project);
        setField(mojo, "reactorProjects", List.of(project));
        return mojo;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = CorrectnessLintMojo.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private boolean containsMessage(Throwable throwable, String fragment) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(fragment)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
