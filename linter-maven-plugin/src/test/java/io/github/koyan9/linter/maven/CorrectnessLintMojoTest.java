/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.LintSeverity;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;
import io.github.koyan9.linter.core.spi.LintRuleProvider;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
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
    void discoversExternalRuleProvidersDuringMojoExecution() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class ExternalController {

                    @GetMapping("/external")
                    public String external() {
                        return "ok";
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-external-provider");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "enabledRules", "EXTERNAL_PROVIDER_RULE");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        ClassLoader providerClassLoader = classLoaderForProviders(ExternalProviderRuleProvider.class);
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(providerClassLoader);
            mojo.execute();
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"ruleId\": \"EXTERNAL_PROVIDER_RULE\""));
        assertTrue(json.contains("External provider observed request-mapped method 'external'."));
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
    void failOnSeverityTakesPrecedenceOverFailOnError() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-fail-on-precedence");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "failOnSeverity", "ERROR");
        setField(mojo, "failOnError", true);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        assertTrue(Files.exists(reportsDirectory.resolve("lint-report.json")));
    }

    @Test
    void failsBuildWhenFailOnErrorIsTrueWithoutSeverityThreshold() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-fail-on-error");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "failOnError", true);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(Files.exists(reportsDirectory.resolve("lint-report.json")));
    }

    @Test
    void rejectsInvalidFailOnSeverityValue() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class AsyncOnly {
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-invalid-severity"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "failOnSeverity", "not-a-severity");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(containsMessage(exception, "Invalid failOnSeverity value"));
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
    void rejectsUnknownEnabledRuleIds() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class AsyncOnly {
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-invalid-rule-id"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "enabledRules", "RULE_DOES_NOT_EXIST");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(containsMessage(exception, "Unknown rule id(s) in enabledRules"));
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
    void rejectsInvalidSeverityOverridesSyntax() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class AsyncOnly {
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-invalid-overrides"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "severityOverrides", "SPRING_ASYNC_VOID");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(containsMessage(exception, "Invalid severityOverrides entry"));
    }

    @Test
    void rejectsUnknownSeverityOverrideRuleIds() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class AsyncOnly {
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-unknown-override-rule"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "severityOverrides", "RULE_DOES_NOT_EXIST=WARNING");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(containsMessage(exception, "Unknown rule id(s) in severityOverrides"));
    }

    @Test
    void rejectsOverlappingEnabledAndDisabledRules() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class AsyncOnly {
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-overlapping-rules"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "enabledRules", "SPRING_ASYNC_VOID");
        setField(mojo, "disabledRules", "SPRING_ASYNC_VOID");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(containsMessage(exception, "Rules cannot be both enabled and disabled"));
    }

    @Test
    void rejectsOverlappingEnabledAndDisabledRuleDomains() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class AsyncOnly {
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-overlapping-domains"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "enabledRuleDomains", "ASYNC");
        setField(mojo, "disabledRuleDomains", "ASYNC");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(containsMessage(exception, "Rule domains cannot be both enabled and disabled"));
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
    void invalidatesIncrementalCacheWhenCentralizedSecurityContextChanges() throws Exception {
        Path sourceDirectory = writeSource("""
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
        Path reportsDirectory = tempDir.resolve("target/reports-security-cache");
        Path cacheFile = tempDir.resolve("target/security-analysis-cache.txt");

        CorrectnessLintMojo firstRun = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(firstRun, "applyBaseline", false);
        setField(firstRun, "useIncrementalCache", true);
        setField(firstRun, "autoDetectCentralizedSecurity", true);
        setField(firstRun, "cacheFile", cacheFile.toFile());
        setField(firstRun, "formats", new LinkedHashSet<>(Set.of("json")));
        firstRun.execute();

        Path demoDirectory = tempDir.resolve("src/main/java/demo");
        Files.writeString(demoDirectory.resolve("SecurityConfig.java"), """
                package demo;

                import org.springframework.context.annotation.Bean;
                import org.springframework.security.web.SecurityFilterChain;

                class SecurityConfig {

                    @Bean
                    SecurityFilterChain filterChain() {
                        return null;
                    }
                }
                """);

        CorrectnessLintMojo secondRun = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(secondRun, "applyBaseline", false);
        setField(secondRun, "useIncrementalCache", true);
        setField(secondRun, "autoDetectCentralizedSecurity", true);
        setField(secondRun, "cacheFile", cacheFile.toFile());
        setField(secondRun, "formats", new LinkedHashSet<>(Set.of("json")));
        secondRun.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertTrue(json.contains("\"cachedFileCount\": 0"));
        assertTrue(json.contains("\"cacheMissReasons\""));
        assertTrue(json.contains("\"auto-detect-context-changed\""));
    }

    @Test
    void autoDetectsConcreteSecurityFilterChainImplementation() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.context.annotation.Bean;
                import org.springframework.security.web.SecurityFilterChain;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                class DemoSecurityFilterChain implements SecurityFilterChain {
                }

                class SecurityConfig {

                    @Bean
                    DemoSecurityFilterChain filterChain() {
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
        Path reportsDirectory = tempDir.resolve("target/reports-security-concrete-chain");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "autoDetectCentralizedSecurity", true);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertFalse(json.contains("\"ruleId\": \"SPRING_ENDPOINT_SECURITY\""));
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
    void skipsNonExecutionRootModuleWhenScanReactorModulesEnabled() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-non-root-reactor");
        Path baselineFile = tempDir.resolve("non-root-baseline.txt");

        MavenProject project = new MavenProject();
        project.setArtifactId("module-a");
        project.setFile(tempDir.resolve("module-a/pom.xml").toFile());
        project.setExecutionRoot(false);
        project.getCompileSourceRoots().add(sourceDirectory.toString());

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                baselineFile
        );
        setField(mojo, "scanReactorModules", true);
        setField(mojo, "writeBaseline", true);
        setField(mojo, "project", project);
        setField(mojo, "reactorProjects", List.of(project));

        mojo.execute();

        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.json")));
        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.html")));
        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.sarif.json")));
        assertFalse(Files.exists(baselineFile));
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
    void ignoresEmptyExecutionRootSourceDirectoryDuringReactorScan() throws Exception {
        Path emptyRootSourceDirectory = tempDir.resolve("src/main/java");
        Files.createDirectories(emptyRootSourceDirectory);
        Path moduleASourceDirectory = writeModuleSource("module-a", "ModuleAAsyncOnly.java", """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class ModuleAAsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path moduleBSourceDirectory = writeModuleSource("module-b", "ModuleBAsyncOnly.java", """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class ModuleBAsyncOnly {

                    @Async
                    private void runAsync() {
                    }
                }
                """);

        MavenProject rootProject = mavenProject("reactor-parent", tempDir.resolve("pom.xml"), true);
        MavenProject moduleAProject = mavenProject("module-a", tempDir.resolve("module-a/pom.xml"), false, moduleASourceDirectory);
        MavenProject moduleBProject = mavenProject("module-b", tempDir.resolve("module-b/pom.xml"), false, moduleBSourceDirectory);

        CorrectnessLintMojo mojo = configuredMojo(
                emptyRootSourceDirectory,
                tempDir.resolve("target/reactor-aggregator-reports"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "scanReactorModules", true);
        setField(mojo, "project", rootProject);
        setField(mojo, "reactorProjects", List.of(rootProject, moduleAProject, moduleBProject));
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(tempDir.resolve("target/reactor-aggregator-reports/lint-report.json"));
        assertTrue(json.contains("\"sourceDirectoryCount\": 2"));
        assertTrue(json.contains("\"moduleId\": \"module-a\""));
        assertTrue(json.contains("\"moduleId\": \"module-b\""));
        assertFalse(json.contains("\"moduleId\": \"reactor-parent\""));
        assertFalse(json.contains("\"module\": \"reactor-parent\""));
    }

    @Test
    void skipsPerModuleOutputsForEmptyExecutionRootSourceDirectory() throws Exception {
        Path emptyRootSourceDirectory = tempDir.resolve("src/main/java");
        Files.createDirectories(emptyRootSourceDirectory);
        Path moduleASourceDirectory = writeModuleSource("module-a", "ModuleAAsyncOnly.java", """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class ModuleAAsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path moduleBSourceDirectory = writeModuleSource("module-b", "ModuleBAsyncOnly.java", """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class ModuleBAsyncOnly {

                    @Async
                    private void runAsync() {
                    }
                }
                """);

        MavenProject rootProject = mavenProject("reactor-parent", tempDir.resolve("pom.xml"), true);
        MavenProject moduleAProject = mavenProject("module-a", tempDir.resolve("module-a/pom.xml"), false, moduleASourceDirectory);
        MavenProject moduleBProject = mavenProject("module-b", tempDir.resolve("module-b/pom.xml"), false, moduleBSourceDirectory);

        Path baselinePath = tempDir.resolve("spring-correctness-linter-baseline.txt");
        Path cachePath = tempDir.resolve("target/analysis-cache.txt");

        CorrectnessLintMojo mojo = configuredMojo(
                emptyRootSourceDirectory,
                tempDir.resolve("target/reactor-aggregator-split-reports"),
                baselinePath
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "writeBaseline", true);
        setField(mojo, "scanReactorModules", true);
        setField(mojo, "splitBaselineByModule", true);
        setField(mojo, "splitCacheByModule", true);
        setField(mojo, "useIncrementalCache", true);
        setField(mojo, "cacheFile", cachePath.toFile());
        setField(mojo, "project", rootProject);
        setField(mojo, "reactorProjects", List.of(rootProject, moduleAProject, moduleBProject));
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        assertTrue(Files.exists(tempDir.resolve("modules/module-a/spring-correctness-linter-baseline.txt")));
        assertTrue(Files.exists(tempDir.resolve("modules/module-b/spring-correctness-linter-baseline.txt")));
        assertFalse(Files.exists(tempDir.resolve("modules/reactor-parent")));
        assertTrue(Files.exists(tempDir.resolve("target/modules/module-a/analysis-cache.txt")));
        assertTrue(Files.exists(tempDir.resolve("target/modules/module-b/analysis-cache.txt")));
        assertFalse(Files.exists(tempDir.resolve("target/modules/reactor-parent")));
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
    void autoDetectsConcreteSecurityFilterChainImplementationForCentralizedSecurity() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.context.annotation.Bean;
                import org.springframework.security.web.SecurityFilterChain;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                class DemoSecurityFilterChain implements SecurityFilterChain {
                }

                class SecurityConfig {

                    @Bean
                    DemoSecurityFilterChain filterChain() {
                        return new DemoSecurityFilterChain();
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
        Path reportsDirectory = tempDir.resolve("target/reports-centralized-security-concrete");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "autoDetectCentralizedSecurity", true);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 0"));
        assertFalse(json.contains("\"ruleId\": \"SPRING_ENDPOINT_SECURITY\""));
    }

    @Test
    void autoDetectsComponentScannedSecurityFilterChainForCentralizedSecurity() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.security.web.SecurityFilterChain;
                import org.springframework.stereotype.Component;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @Component
                class DemoSecurityFilterChain implements SecurityFilterChain {
                }

                @RestController
                class PublicController {

                    @GetMapping("/open")
                    public String open() {
                        return "ok";
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-centralized-security-component");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "autoDetectCentralizedSecurity", true);
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
    void writesRuleReferenceWithDomainCoverageSnapshot() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-rule-reference-snapshot");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        String markdown = Files.readString(reportsDirectory.resolve("rules-reference.md"));
        assertTrue(markdown.contains("## Domain Coverage Snapshot"));
        assertTrue(markdown.contains("| `ASYNC` |"));
        assertTrue(markdown.contains("Domains with the most complete proxy-boundary coverage today are `ASYNC`, `CACHE`, and `TRANSACTION`."));
    }

    @Test
    void skipsBaselineDiffWhenDisabled() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-no-baseline-diff");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "writeBaselineDiff", false);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        assertTrue(Files.exists(reportsDirectory.resolve("lint-report.json")));
        assertFalse(Files.exists(reportsDirectory.resolve("baseline-diff.json")));
        assertFalse(Files.exists(reportsDirectory.resolve("baseline-diff.html")));
    }

    @Test
    void governanceSnapshotReflectsEnabledDomains() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-domains");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "enabledRuleDomains", "async");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        Path governanceFile = reportsDirectory.resolve("rules-governance.json");
        assertTrue(Files.exists(governanceFile));
        String json = Files.readString(governanceFile);
        assertTrue(json.contains("\"enabledDomains\""));
        assertTrue(json.contains("\"enabledDomains\": [\"ASYNC\"]"));
        assertTrue(json.contains("\"effectiveDomains\": [\"ASYNC\"]"));
        assertFalse(json.contains("\"domain\": \"TRANSACTION\""));
    }

    @Test
    void writesRuleDocsToConfiguredRelativePath() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-custom-rule-docs");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "ruleDocsFileName", "docs/custom-rules.md");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        assertTrue(Files.exists(reportsDirectory.resolve("docs/custom-rules.md")));
        assertFalse(Files.exists(reportsDirectory.resolve("rules-reference.md")));
        assertTrue(Files.exists(reportsDirectory.resolve("rules-governance.json")));
    }

    @Test
    void fallsBackToDefaultRuleDocsFileNameWhenBlank() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-blank-rule-docs-name");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "ruleDocsFileName", "   ");
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        assertTrue(Files.exists(reportsDirectory.resolve("rules-reference.md")));
        assertTrue(Files.exists(reportsDirectory.resolve("rules-governance.json")));
    }

    @Test
    void writesGovernanceOutputsEvenWhenNoCoreFormatsAreValid() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-governance-only");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("markdown")));

        mojo.execute();

        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.json")));
        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.html")));
        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.sarif.json")));
        assertTrue(Files.exists(reportsDirectory.resolve("rules-reference.md")));
        assertTrue(Files.exists(reportsDirectory.resolve("rules-governance.json")));
    }

    @Test
    void writesValidFormatsEvenWhenUnknownFormatsArePresent() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-mixed-formats");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("JSON", "markdown")));

        mojo.execute();

        assertTrue(Files.exists(reportsDirectory.resolve("lint-report.json")));
        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.html")));
        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.sarif.json")));
        assertTrue(Files.exists(reportsDirectory.resolve("rules-reference.md")));
        assertTrue(Files.exists(reportsDirectory.resolve("rules-governance.json")));
    }

    @Test
    void writesBaselineDiffEvenWhenNoCoreFormatsAreValid() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-baseline-diff-only");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("markdown")));

        mojo.execute();

        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.json")));
        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.html")));
        assertFalse(Files.exists(reportsDirectory.resolve("lint-report.sarif.json")));
        assertTrue(Files.exists(reportsDirectory.resolve("baseline-diff.json")));
        assertTrue(Files.exists(reportsDirectory.resolve("baseline-diff.html")));
        assertTrue(Files.exists(reportsDirectory.resolve("rules-reference.md")));
        assertTrue(Files.exists(reportsDirectory.resolve("rules-governance.json")));
    }

    @Test
    void skipsRuleDocsAndGovernanceOutputsWhenDisabled() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class AsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-no-rule-docs");

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "writeRuleDocs", false);
        setField(mojo, "formats", new LinkedHashSet<>(Set.of("json")));

        mojo.execute();

        assertTrue(Files.exists(reportsDirectory.resolve("lint-report.json")));
        assertFalse(Files.exists(reportsDirectory.resolve("rules-reference.md")));
        assertFalse(Files.exists(reportsDirectory.resolve("rules-governance.json")));
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
        assertTrue(json.contains("\"runtimeSummary\""));
        assertTrue(json.contains("\"cacheHitRatePercent\""));
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
    void includesTestSourceRootsWhenEnabled() throws Exception {
        Path mainSourceDirectory = tempDir.resolve("src/main/java/demo");
        Path testSourceDirectory = tempDir.resolve("src/test/java/demo");
        Files.createDirectories(mainSourceDirectory);
        Files.createDirectories(testSourceDirectory);
        Files.writeString(mainSourceDirectory.resolve("MainOnly.java"), """
                package demo;

                class MainOnly {
                }
                """);
        Files.writeString(testSourceDirectory.resolve("TestAsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class TestAsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-test-roots");
        MavenProject project = mavenProject("empty-project", tempDir.resolve("pom.xml"), true, tempDir.resolve("src/main/java"));
        project.getTestCompileSourceRoots().add(tempDir.resolve("src/test/java").toString());

        CorrectnessLintMojo defaultMojo = configuredMojo(
                tempDir.resolve("src/main/java"),
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(defaultMojo, "project", project);
        setField(defaultMojo, "applyBaseline", false);
        setField(defaultMojo, "formats", new LinkedHashSet<>(Set.of("json")));
        defaultMojo.execute();

        String defaultJson = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(defaultJson.contains("\"issueCount\": 0"));

        CorrectnessLintMojo includeTestsMojo = configuredMojo(
                tempDir.resolve("src/main/java"),
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(includeTestsMojo, "project", project);
        setField(includeTestsMojo, "applyBaseline", false);
        setField(includeTestsMojo, "includeTestSourceRoots", true);
        setField(includeTestsMojo, "formats", new LinkedHashSet<>(Set.of("json")));
        includeTestsMojo.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 1"));
        assertTrue(json.contains("\"ruleId\": \"SPRING_ASYNC_VOID\""));
        assertTrue(json.contains("TestAsyncOnly.java"));
    }

    @Test
    void invalidatesIncrementalCacheWhenIncludeTestSourceRootsChanges() throws Exception {
        Path mainSourceDirectory = tempDir.resolve("src/main/java/demo");
        Path testSourceDirectory = tempDir.resolve("src/test/java/demo");
        Files.createDirectories(mainSourceDirectory);
        Files.createDirectories(testSourceDirectory);
        Files.writeString(mainSourceDirectory.resolve("MainOnly.java"), """
                package demo;

                class MainOnly {
                }
                """);
        Files.writeString(testSourceDirectory.resolve("TestAsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class TestAsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-test-roots-cache");
        Path cacheFile = tempDir.resolve("target/test-roots-analysis-cache.txt");
        MavenProject project = mavenProject("empty-project", tempDir.resolve("pom.xml"), true, tempDir.resolve("src/main/java"));
        project.getTestCompileSourceRoots().add(tempDir.resolve("src/test/java").toString());

        CorrectnessLintMojo firstRun = configuredMojo(
                tempDir.resolve("src/main/java"),
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(firstRun, "project", project);
        setField(firstRun, "reactorProjects", List.of(project));
        setField(firstRun, "applyBaseline", false);
        setField(firstRun, "useIncrementalCache", true);
        setField(firstRun, "cacheFile", cacheFile.toFile());
        setField(firstRun, "formats", new LinkedHashSet<>(Set.of("json")));
        firstRun.execute();

        CorrectnessLintMojo secondRun = configuredMojo(
                tempDir.resolve("src/main/java"),
                reportsDirectory,
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(secondRun, "project", project);
        setField(secondRun, "reactorProjects", List.of(project));
        setField(secondRun, "applyBaseline", false);
        setField(secondRun, "useIncrementalCache", true);
        setField(secondRun, "includeTestSourceRoots", true);
        setField(secondRun, "cacheFile", cacheFile.toFile());
        setField(secondRun, "formats", new LinkedHashSet<>(Set.of("json")));
        secondRun.execute();

        String json = Files.readString(reportsDirectory.resolve("lint-report.json"));
        assertTrue(json.contains("\"issueCount\": 1"));
        assertTrue(json.contains("\"cachedFileCount\": 0"));
        assertTrue(json.contains("TestAsyncOnly.java"));
        assertTrue(json.contains("\"source-roots-changed\""));
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
    void keepsStaleBaselineEntriesInOwningModuleWhenSplitBaselineIsEnabled() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class BaseService {
                }
                """);
        Path extraRoot = tempDir.resolve("custom-src/demo");
        Files.createDirectories(extraRoot);
        Files.writeString(extraRoot.resolve("ExtraAsyncOnly.java"), """
                package demo;

                import org.springframework.scheduling.annotation.Async;

                class ExtraAsyncOnly {

                    @Async
                    public void runAsync() {
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-module-stale-baseline");
        Path baselineFile = tempDir.resolve("spring-correctness-linter-baseline.txt");

        CorrectnessLintMojo firstRun = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                baselineFile
        );
        setField(firstRun, "applyBaseline", false);
        setField(firstRun, "writeBaseline", true);
        setField(firstRun, "splitBaselineByModule", true);
        setField(firstRun, "moduleSourceDirectories", "empty-project=custom-src");
        setField(firstRun, "formats", new LinkedHashSet<>(Set.of("json")));
        firstRun.execute();

        Files.delete(extraRoot.resolve("ExtraAsyncOnly.java"));

        CorrectnessLintMojo secondRun = configuredMojo(
                sourceDirectory,
                reportsDirectory,
                baselineFile
        );
        setField(secondRun, "splitBaselineByModule", true);
        setField(secondRun, "moduleSourceDirectories", "empty-project=custom-src");
        setField(secondRun, "formats", new LinkedHashSet<>(Set.of("json")));
        secondRun.execute();

        String baselineDiffJson = Files.readString(reportsDirectory.resolve("baseline-diff.json"));
        assertTrue(baselineDiffJson.contains("\"moduleId\": \"empty-project\""));
        assertTrue(baselineDiffJson.contains("\"staleBaselineCount\": 1"));
        assertFalse(baselineDiffJson.contains("\"moduleId\": \"custom-src\""));
    }

    @Test
    void rejectsUnknownModuleSourceDirectoriesModuleId() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class BaseService {
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-unknown-module-root"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "moduleSourceDirectories", "unknown-module=src/generated/java");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(containsMessage(exception, "Unknown module id(s) in moduleSourceDirectories"));
    }

    @Test
    void rejectsInvalidModuleSourceDirectoriesSyntax() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class BaseService {
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-invalid-module-roots"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "moduleSourceDirectories", "broken-entry");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(containsMessage(exception, "Invalid moduleSourceDirectories entry"));
    }

    @Test
    void rejectsBlankModuleSourceDirectoriesPathList() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                class BaseService {
                }
                """);

        CorrectnessLintMojo mojo = configuredMojo(
                sourceDirectory,
                tempDir.resolve("target/reports-blank-module-roots"),
                tempDir.resolve("spring-correctness-linter-baseline.txt")
        );
        setField(mojo, "applyBaseline", false);
        setField(mojo, "moduleSourceDirectories", "empty-project=");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(containsMessage(exception, "Invalid moduleSourceDirectories entry"));
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
    void autoDetectsProjectWideConcreteKeyGeneratorBean() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.cache.interceptor.KeyGenerator;
                import org.springframework.context.annotation.Bean;

                class DemoKeyGenerator implements KeyGenerator {

                    @Override
                    public Object generate(Object target, java.lang.reflect.Method method, Object... params) {
                        return params.length;
                    }
                }

                class ProjectKeyGeneratorConfig {

                    @Bean
                    DemoKeyGenerator keyGenerator() {
                        return new DemoKeyGenerator();
                    }
                }

                class CacheService {

                    @Cacheable(cacheNames = "demo")
                    public String load(String id) {
                        return id;
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-project-key-generator-concrete");

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
    void autoDetectsComponentScannedKeyGenerator() throws Exception {
        Path sourceDirectory = writeSource("""
                package demo;

                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.cache.interceptor.KeyGenerator;
                import org.springframework.stereotype.Component;

                @Component
                class DemoKeyGenerator implements KeyGenerator {

                    @Override
                    public Object generate(Object target, java.lang.reflect.Method method, Object... params) {
                        return params.length;
                    }
                }

                class CacheService {

                    @Cacheable(cacheNames = "demo")
                    public String load(String id) {
                        return id;
                    }
                }
                """);
        Path reportsDirectory = tempDir.resolve("target/reports-component-key-generator");

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

    private Path writeModuleSource(String moduleId, String fileName, String content) throws Exception {
        Path sourceDirectory = tempDir.resolve(moduleId).resolve("src/main/java/demo");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve(fileName), content);
        return tempDir.resolve(moduleId).resolve("src/main/java");
    }

    private MavenProject mavenProject(String artifactId, Path pomPath, boolean executionRoot, Path... compileSourceRoots) {
        MavenProject project = new MavenProject();
        project.setArtifactId(artifactId);
        project.setFile(pomPath.toFile());
        project.setExecutionRoot(executionRoot);
        for (Path compileSourceRoot : compileSourceRoots) {
            project.getCompileSourceRoots().add(compileSourceRoot.toString());
        }
        return project;
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

    private ClassLoader classLoaderForProviders(Class<?>... providerClasses) throws Exception {
        Path servicesDirectory = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDirectory);
        Files.writeString(
                servicesDirectory.resolve("io.github.koyan9.linter.core.spi.LintRuleProvider"),
                String.join(System.lineSeparator(), java.util.Arrays.stream(providerClasses).map(Class::getName).toList())
        );
        URL[] urls = { tempDir.toUri().toURL() };
        return new URLClassLoader(urls, getClass().getClassLoader());
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

    public static final class ExternalProviderRuleProvider implements LintRuleProvider {

        @Override
        public List<LintRule> rules() {
            return List.of(new ExternalProviderRule());
        }
    }

    public static final class ExternalProviderRule implements LintRule {

        @Override
        public String id() {
            return "EXTERNAL_PROVIDER_RULE";
        }

        @Override
        public String title() {
            return "External provider rule";
        }

        @Override
        public String description() {
            return "Verifies external lint rule discovery during Mojo execution.";
        }

        @Override
        public LintSeverity severity() {
            return LintSeverity.INFO;
        }

        @Override
        public RuleDomain domain() {
            return RuleDomain.GENERAL;
        }

        @Override
        public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
            SpringSemanticFacts facts = context.springFacts(sourceUnit);
            java.util.ArrayList<LintIssue> issues = new java.util.ArrayList<>();
            for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
                if (!facts.typeFacts(typeDeclaration).isWebController()) {
                    continue;
                }
                for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                    if (!facts.methodFacts(typeDeclaration, method).isPublicRequestMapping()) {
                        continue;
                    }
                    issues.add(new LintIssue(
                            id(),
                            severity(),
                            "External provider observed request-mapped method '" + method.getNameAsString() + "'.",
                            sourceUnit.path(),
                            JavaSourceInspector.lineOf(method)
                    ));
                }
            }
            return issues;
        }
    }
}
