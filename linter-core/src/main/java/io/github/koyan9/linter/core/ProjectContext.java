/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProjectContext {

    private static final Set<String> SECURITY_FILTER_CHAIN_TYPES = Set.of(
            "org.springframework.security.web.SecurityFilterChain",
            "org.springframework.security.web.server.SecurityWebFilterChain"
    );
    private static final Set<String> KEY_GENERATOR_TYPES = Set.of("org.springframework.cache.interceptor.KeyGenerator");
    private static final Set<String> CACHING_CONFIGURER_TYPES = Set.of(
            "org.springframework.cache.annotation.CachingConfigurer",
            "org.springframework.cache.annotation.CachingConfigurerSupport"
    );
    private static final Set<String> COMPONENT_STEREOTYPE_TYPES = Set.of(
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.context.annotation.Configuration"
    );

    private final Path projectRoot;
    private final Path sourceDirectory;
    private final List<SourceRoot> sourceRoots;
    private final List<SourceDocument> sourceDocuments;
    private final Map<Path, JavaSourceInspector.ParseOutcome> parseOutcomes;
    private final AnnotationMetadataIndex annotationMetadataIndex;
    private final LintOptions options;
    private final Map<Path, SourceUnit> sourceUnitsByPath = new ConcurrentHashMap<>();
    private final Map<Path, SpringSemanticFacts> semanticFactsBySourcePath = new ConcurrentHashMap<>();
    private volatile TypeResolutionIndex typeResolutionIndex;
    private volatile Boolean securityFilterChainBeanPresent;
    private volatile Boolean keyGeneratorBeanPresent;
    private volatile List<String> annotationFingerprintEntries;
    private volatile List<String> typeResolutionFingerprintEntries;
    private volatile List<String> autoDetectFingerprintEntries;

    private ProjectContext(
            Path projectRoot,
            Path sourceDirectory,
            List<SourceRoot> sourceRoots,
            List<SourceDocument> sourceDocuments,
            Map<Path, JavaSourceInspector.ParseOutcome> parseOutcomes,
            AnnotationMetadataIndex annotationMetadataIndex,
            LintOptions options
    ) {
        this.projectRoot = projectRoot;
        this.sourceDirectory = sourceDirectory;
        this.sourceRoots = List.copyOf(sourceRoots);
        this.sourceDocuments = List.copyOf(sourceDocuments);
        this.parseOutcomes = Map.copyOf(parseOutcomes);
        this.annotationMetadataIndex = annotationMetadataIndex;
        this.options = options == null ? LintOptions.defaults() : options;
    }

    public static ProjectContext load(Path projectRoot, Path sourceDirectory) throws IOException {
        return loadSourceRoots(projectRoot, List.of(SourceRoot.of(projectRoot, sourceDirectory)), LintOptions.defaults());
    }

    public static ProjectContext load(Path projectRoot, List<Path> sourceDirectories) throws IOException {
        return loadSourceRoots(projectRoot, sourceDirectories.stream().map(path -> SourceRoot.of(projectRoot, path)).toList(), LintOptions.defaults());
    }

    public static ProjectContext loadSourceRoots(Path projectRoot, List<SourceRoot> sourceRoots) throws IOException {
        return loadSourceRoots(projectRoot, sourceRoots, LintOptions.defaults());
    }

    public static ProjectContext loadSourceRoots(Path projectRoot, List<SourceRoot> sourceRoots, LintOptions options) throws IOException {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        List<SourceRoot> normalizedSourceRoots = sourceRoots.stream()
                .map(sourceRoot -> new SourceRoot(sourceRoot.path().toAbsolutePath().normalize(), sourceRoot.moduleId()))
                .filter(sourceRoot -> Files.isDirectory(sourceRoot.path()))
                .distinct()
                .toList();

        Map<Path, SourceDocument> documentsByPath = new LinkedHashMap<>();
        Map<Path, JavaSourceInspector.ParseOutcome> parseOutcomes = new LinkedHashMap<>();
        List<SourceRoot> effectiveSourceRoots = new ArrayList<>();
        for (SourceRoot sourceRoot : normalizedSourceRoots) {
            Path normalizedSource = sourceRoot.path();
            try (Stream<Path> stream = Files.walk(normalizedSource)) {
                List<SourceDocument> sourceDocuments = stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(path -> readDocument(path, sourceRoot.moduleId(), parseOutcomes))
                        .toList();
                if (sourceDocuments.isEmpty()) {
                    continue;
                }
                effectiveSourceRoots.add(sourceRoot);
                sourceDocuments.forEach(document -> documentsByPath.put(document.path(), document));
            }
        }

        Path primarySourceDirectory = effectiveSourceRoots.isEmpty() ? normalizedRoot : effectiveSourceRoots.get(0).path();

        if (effectiveSourceRoots.isEmpty()) {
            return new ProjectContext(normalizedRoot, primarySourceDirectory, List.of(), List.of(), Map.of(), AnnotationMetadataIndex.empty(), options);
        }

        return new ProjectContext(
                normalizedRoot,
                primarySourceDirectory,
                effectiveSourceRoots,
                documentsByPath.values().stream().collect(Collectors.toList()),
                parseOutcomes,
                AnnotationMetadataIndex.build(documentsByPath.values().stream().collect(Collectors.toList()), parseOutcomes),
                options
        );
    }

    private static SourceDocument readDocument(Path path, String moduleId, Map<Path, JavaSourceInspector.ParseOutcome> parseOutcomes) {
        try {
            Path normalizedPath = path.toAbsolutePath().normalize();
            byte[] bytes = Files.readAllBytes(normalizedPath);
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (content.contains("@interface")) {
                parseOutcomes.put(normalizedPath, JavaSourceInspector.inspect(content));
            }
            return new SourceDocument(normalizedPath, content, SourceDocument.sha256(bytes), moduleId);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read source file: " + path, exception);
        }
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path sourceDirectory() {
        return sourceDirectory;
    }

    public List<Path> sourceDirectories() {
        return sourceRoots.stream().map(SourceRoot::path).toList();
    }

    public List<SourceRoot> sourceRoots() {
        return sourceRoots;
    }

    public List<SourceDocument> sourceDocuments() {
        return sourceDocuments;
    }

    public List<SourceUnit> sourceUnits() {
        return sourceDocuments.stream()
                .map(this::sourceUnitFor)
                .toList();
    }

    public AnnotationMetadataIndex annotationMetadataIndex() {
        return annotationMetadataIndex;
    }

    public LintOptions options() {
        return options;
    }

    JavaSourceInspector.ParseOutcome parseOutcomeFor(SourceDocument sourceDocument) {
        return parseOutcomes.get(sourceDocument.path());
    }

    SourceUnit sourceUnitFor(SourceDocument sourceDocument) {
        Path key = sourceDocument.path().toAbsolutePath().normalize();
        return sourceUnitsByPath.computeIfAbsent(
                key,
                ignored -> sourceDocument.toSourceUnit(parseOutcomeFor(sourceDocument))
        );
    }

    public SpringSemanticFacts springFacts(SourceUnit sourceUnit) {
        Path key = sourceUnit.path().toAbsolutePath().normalize();
        return semanticFactsBySourcePath.computeIfAbsent(key, ignored -> SpringSemanticFacts.create(this));
    }

    public TypeResolutionIndex typeResolutionIndex() {
        TypeResolutionIndex cached = typeResolutionIndex;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (typeResolutionIndex != null) {
                return typeResolutionIndex;
            }
            TypeResolutionIndex built = TypeResolutionIndex.build(this);
            typeResolutionIndex = built;
            return built;
        }
    }

    public boolean hasSecurityFilterChainBean() {
        Boolean cached = securityFilterChainBeanPresent;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (securityFilterChainBeanPresent != null) {
                return securityFilterChainBeanPresent;
            }
            boolean found = false;
            for (SourceDocument sourceDocument : sourceDocuments) {
                SourceUnit sourceUnit = sourceUnitFor(sourceDocument);
                SpringSemanticFacts facts = springFacts(sourceUnit);
                for (MethodDeclaration method : sourceUnit.structure().methods()) {
                    if (!facts.hasAnnotation(method, "Bean")) {
                        continue;
                    }
                    if (isSecurityFilterChainType(method.getType().toString(), sourceUnit)) {
                        found = true;
                        break;
                    }
                }
                if (!found && declaresComponentScannedSecurityFilterChain(sourceUnit)) {
                    found = true;
                }
                if (found) {
                    break;
                }
            }
            securityFilterChainBeanPresent = found;
            return found;
        }
    }

    public boolean hasProjectWideKeyGeneratorBean() {
        Boolean cached = keyGeneratorBeanPresent;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (keyGeneratorBeanPresent != null) {
                return keyGeneratorBeanPresent;
            }
            boolean found = false;
            for (SourceDocument sourceDocument : sourceDocuments) {
                SourceUnit sourceUnit = sourceUnitFor(sourceDocument);
                SpringSemanticFacts facts = springFacts(sourceUnit);
                for (MethodDeclaration method : sourceUnit.structure().methods()) {
                    if (!facts.hasAnnotation(method, "Bean")) {
                        continue;
                    }
                    if (isKeyGeneratorType(method.getType().toString(), sourceUnit)) {
                        found = true;
                        break;
                    }
                }
                if (!found && declaresProjectWideKeyGenerator(sourceUnit)) {
                    found = true;
                }
                if (!found && declaresComponentScannedKeyGenerator(sourceUnit)) {
                    found = true;
                }
                if (found) {
                    break;
                }
            }
            keyGeneratorBeanPresent = found;
            return found;
        }
    }

    private boolean declaresProjectWideKeyGenerator(SourceUnit sourceUnit) {
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            if (!isCachingConfigurerType(typeDeclaration, sourceUnit)) {
                continue;
            }
            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                if (!"keyGenerator".equals(method.getNameAsString())) {
                    continue;
                }
                if (!method.getParameters().isEmpty()) {
                    continue;
                }
                if (isKeyGeneratorType(method.getType().toString(), sourceUnit)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean declaresComponentScannedKeyGenerator(SourceUnit sourceUnit) {
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            if (!isConcreteComponentCandidate(typeDeclaration)) {
                continue;
            }
            if (!isKeyGeneratorImplementationType(typeDeclaration, sourceUnit)) {
                continue;
            }
            if (hasDirectSpringComponentStereotype(typeDeclaration, sourceUnit)) {
                return true;
            }
        }
        return false;
    }

    private boolean declaresComponentScannedSecurityFilterChain(SourceUnit sourceUnit) {
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            if (!isConcreteComponentCandidate(typeDeclaration)) {
                continue;
            }
            if (!isSecurityFilterChainImplementationType(typeDeclaration, sourceUnit)) {
                continue;
            }
            if (hasDirectSpringComponentStereotype(typeDeclaration, sourceUnit)) {
                return true;
            }
        }
        return false;
    }

    public List<String> annotationFingerprintEntries() {
        List<String> cached = annotationFingerprintEntries;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (annotationFingerprintEntries != null) {
                return annotationFingerprintEntries;
            }
            List<String> built = buildAnnotationFingerprintEntries();
            annotationFingerprintEntries = built;
            return built;
        }
    }

    public List<String> sourceRootFingerprintEntries() {
        return sourceRoots.stream()
                .map(sourceRoot -> "source-root\t" + relativePathForFingerprint(sourceRoot.path()) + '\t' + sourceRoot.moduleId())
                .sorted()
                .toList();
    }

    public List<String> typeResolutionFingerprintEntries() {
        List<String> cached = typeResolutionFingerprintEntries;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (typeResolutionFingerprintEntries != null) {
                return typeResolutionFingerprintEntries;
            }
            List<String> built = buildTypeResolutionFingerprintEntries();
            typeResolutionFingerprintEntries = built;
            return built;
        }
    }

    public List<String> autoDetectFingerprintEntries() {
        List<String> cached = autoDetectFingerprintEntries;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (autoDetectFingerprintEntries != null) {
                return autoDetectFingerprintEntries;
            }
            List<String> built = buildAutoDetectFingerprintEntries();
            autoDetectFingerprintEntries = built;
            return built;
        }
    }

    private List<String> buildAnnotationFingerprintEntries() {
        List<String> entries = new ArrayList<>();
        for (SourceDocument sourceDocument : sourceDocuments) {
            String relativePath = sourceDocument.relativePath(projectRoot);
            if (sourceDocument.content().contains("@interface")) {
                entries.add("annotation\t" + relativePath + '\t' + sourceDocument.contentHash());
            }
        }
        return entries.stream().sorted().toList();
    }

    private List<String> buildTypeResolutionFingerprintEntries() {
        List<String> entries = new ArrayList<>();
        for (SourceDocument sourceDocument : sourceDocuments) {
            SourceUnit sourceUnit = sourceUnitFor(sourceDocument);
            entries.add(buildTypeResolutionSummary(sourceUnit, sourceDocument.relativePath(projectRoot)));
        }
        return entries.stream().sorted().toList();
    }

    private List<String> buildAutoDetectFingerprintEntries() {
        List<String> entries = new ArrayList<>();
        for (SourceDocument sourceDocument : sourceDocuments) {
            SourceUnit sourceUnit = sourceUnitFor(sourceDocument);
            if (isAutoDetectionRelevant(sourceUnit)) {
                entries.add("autodetect\t" + sourceDocument.relativePath(projectRoot) + '\t' + sourceDocument.contentHash());
            }
        }
        return entries.stream().sorted().toList();
    }

    private String relativePathForFingerprint(Path path) {
        try {
            return projectRoot.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (IllegalArgumentException exception) {
            return path.toAbsolutePath().normalize().toString().replace('\\', '/');
        }
    }

    private String buildTypeResolutionSummary(SourceUnit sourceUnit, String relativePath) {
        if (sourceUnit.compilationUnit().isEmpty()) {
            return "types\t" + relativePath + "\tparse=invalid";
        }

        CompilationUnit compilationUnit = sourceUnit.compilationUnit().orElseThrow();
        StringBuilder builder = new StringBuilder("types\t")
                .append(relativePath)
                .append("\tpkg=")
                .append(packageName(sourceUnit));
        compilationUnit.getImports().stream()
                .filter(importDeclaration -> !importDeclaration.isStatic())
                .map(this::importSummary)
                .sorted()
                .forEach(summary -> builder.append("\timport=").append(summary));
        sourceUnit.structure().typeDeclarations().stream()
                .map(typeDeclaration -> typeSummary(sourceUnit, typeDeclaration))
                .sorted()
                .forEach(summary -> builder.append("\ttype=").append(summary));
        return builder.toString();
    }

    private String importSummary(ImportDeclaration importDeclaration) {
        return importDeclaration.getNameAsString() + (importDeclaration.isAsterisk() ? ".*" : "");
    }

    private String typeSummary(SourceUnit sourceUnit, TypeDeclaration<?> typeDeclaration) {
        StringBuilder builder = new StringBuilder()
                .append(qualifiedNameOf(typeDeclaration, packageName(sourceUnit)))
                .append("|ann=")
                .append(String.join(",", directAnnotationNames(typeDeclaration)));
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            builder.append("|extends=")
                    .append(classOrInterfaceDeclaration.getExtendedTypes().stream()
                            .map(type -> normalizeTypeReference(type.toString()))
                            .sorted()
                            .collect(Collectors.joining(",")));
            builder.append("|implements=")
                    .append(classOrInterfaceDeclaration.getImplementedTypes().stream()
                            .map(type -> normalizeTypeReference(type.toString()))
                            .sorted()
                            .collect(Collectors.joining(",")));
        }
        sourceUnit.structure().methodsOf(typeDeclaration).stream()
                .map(this::methodSummary)
                .sorted()
                .forEach(summary -> builder.append("|method=").append(summary));
        return builder.toString();
    }

    private String methodSummary(MethodDeclaration method) {
        return method.getNameAsString()
                + '#'
                + method.getParameters().size()
                + "#varargs="
                + isVarArgs(method)
                + "#return="
                + normalizeTypeReference(method.getType().toString())
                + "#visibility="
                + visibility(method)
                + "#final="
                + method.isFinal()
                + "#ann="
                + String.join(",", directAnnotationNames(method));
    }

    private boolean isAutoDetectionRelevant(SourceUnit sourceUnit) {
        SpringSemanticFacts facts = springFacts(sourceUnit);
        for (MethodDeclaration method : sourceUnit.structure().methods()) {
            if (!facts.hasAnnotation(method, "Bean")) {
                continue;
            }
            if (isSecurityFilterChainType(method.getType().toString(), sourceUnit)
                    || isKeyGeneratorType(method.getType().toString(), sourceUnit)) {
                return true;
            }
        }
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            if (isSecurityFilterChainImplementationType(typeDeclaration, sourceUnit)
                    || isCachingConfigurerType(typeDeclaration, sourceUnit)
                    || isKeyGeneratorImplementationType(typeDeclaration, sourceUnit)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSecurityFilterChainType(String rawType, SourceUnit sourceUnit) {
        return matchesQualifiedType(rawType, sourceUnit, SECURITY_FILTER_CHAIN_TYPES);
    }

    private boolean isKeyGeneratorType(String rawType, SourceUnit sourceUnit) {
        return matchesQualifiedType(rawType, sourceUnit, KEY_GENERATOR_TYPES);
    }

    private boolean isSecurityFilterChainImplementationType(TypeDeclaration<?> typeDeclaration, SourceUnit sourceUnit) {
        TypeResolutionIndex.TypeDescriptor descriptor = typeResolutionIndex().descriptorFor(typeDeclaration, sourceUnit);
        return typeDeclaresOrInheritsQualifiedType(
                typeDeclaration,
                descriptor.packageName(),
                descriptor.importInfo(),
                SECURITY_FILTER_CHAIN_TYPES,
                new LinkedHashSet<>()
        );
    }

    private boolean isKeyGeneratorImplementationType(TypeDeclaration<?> typeDeclaration, SourceUnit sourceUnit) {
        TypeResolutionIndex.TypeDescriptor descriptor = typeResolutionIndex().descriptorFor(typeDeclaration, sourceUnit);
        return typeDeclaresOrInheritsQualifiedType(
                typeDeclaration,
                descriptor.packageName(),
                descriptor.importInfo(),
                KEY_GENERATOR_TYPES,
                new LinkedHashSet<>()
        );
    }

    private boolean isCachingConfigurerType(TypeDeclaration<?> typeDeclaration, SourceUnit sourceUnit) {
        if (!(typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration)) {
            return false;
        }
        for (ClassOrInterfaceType implementedType : classOrInterfaceDeclaration.getImplementedTypes()) {
            if (matchesQualifiedType(implementedType.toString(), sourceUnit, CACHING_CONFIGURER_TYPES)) {
                return true;
            }
        }
        for (ClassOrInterfaceType extendedType : classOrInterfaceDeclaration.getExtendedTypes()) {
            if (matchesQualifiedType(extendedType.toString(), sourceUnit, CACHING_CONFIGURER_TYPES)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDirectSpringComponentStereotype(TypeDeclaration<?> typeDeclaration, SourceUnit sourceUnit) {
        return typeDeclaration.getAnnotations().stream()
                .map(annotationExpr -> annotationExpr.getNameAsString())
                .anyMatch(annotationName -> matchesQualifiedType(annotationName, sourceUnit, COMPONENT_STEREOTYPE_TYPES));
    }

    private boolean isConcreteComponentCandidate(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return !classOrInterfaceDeclaration.isInterface() && !classOrInterfaceDeclaration.isAbstract();
        }
        return true;
    }

    private boolean matchesQualifiedType(String rawType, SourceUnit sourceUnit, Set<String> expectedQualifiedNames) {
        String normalized = normalizeTypeReference(rawType);
        if (normalized.isBlank()) {
            return false;
        }
        TypeReferenceResolutionContext resolutionContext = typeReferenceResolutionContext(sourceUnit);
        TypeResolutionIndex.ImportInfo importInfo = new TypeResolutionIndex.ImportInfo(
                resolutionContext.explicitImports(),
                resolutionContext.wildcardImports()
        );
        return matchesQualifiedType(
                normalized,
                resolutionContext.packageName(),
                importInfo,
                expectedQualifiedNames,
                new LinkedHashSet<>()
        );
    }

    private boolean matchesQualifiedType(
            String rawType,
            String currentPackage,
            TypeResolutionIndex.ImportInfo importInfo,
            Set<String> expectedQualifiedNames,
            Set<String> visitedTypes
    ) {
        String normalized = normalizeTypeReference(rawType);
        if (normalized.isBlank()) {
            return false;
        }
        if (expectedQualifiedNames.contains(normalized)) {
            return true;
        }
        if (normalized.contains(".")) {
            Optional<TypeResolutionIndex.TypeDescriptor> resolvedType = typeResolutionIndex().resolveTypeReference(normalized, currentPackage, importInfo);
            if (resolvedType.isEmpty()) {
                return false;
            }
            TypeResolutionIndex.TypeDescriptor descriptor = resolvedType.orElseThrow();
            if (!visitedTypes.add(descriptor.qualifiedName())) {
                return false;
            }
            return typeDeclaresOrInheritsQualifiedType(
                    descriptor.declaration(),
                    descriptor.packageName(),
                    descriptor.importInfo(),
                    expectedQualifiedNames,
                    visitedTypes
            );
        }
        if (!currentPackage.isBlank()
                && expectedQualifiedNames.contains(currentPackage + "." + normalized)) {
            return true;
        }
        String explicitImport = importInfo.explicitImports().get(normalized);
        if (explicitImport != null) {
            if (expectedQualifiedNames.contains(explicitImport)) {
                return true;
            }
        }

        String wildcardMatch = null;
        for (String wildcardImport : importInfo.wildcardImports()) {
            String candidate = wildcardImport + "." + normalized;
            if (!expectedQualifiedNames.contains(candidate)) {
                continue;
            }
            if (wildcardMatch != null && !wildcardMatch.equals(candidate)) {
                return false;
            }
            wildcardMatch = candidate;
        }
        if (wildcardMatch != null) {
            return true;
        }

        Optional<TypeResolutionIndex.TypeDescriptor> resolvedType = typeResolutionIndex().resolveTypeReference(normalized, currentPackage, importInfo);
        if (resolvedType.isEmpty()) {
            return false;
        }
        TypeResolutionIndex.TypeDescriptor descriptor = resolvedType.orElseThrow();
        if (!visitedTypes.add(descriptor.qualifiedName())) {
            return false;
        }
        return typeDeclaresOrInheritsQualifiedType(
                descriptor.declaration(),
                descriptor.packageName(),
                descriptor.importInfo(),
                expectedQualifiedNames,
                visitedTypes
        );
    }

    private boolean typeDeclaresOrInheritsQualifiedType(
            TypeDeclaration<?> typeDeclaration,
            String packageName,
            TypeResolutionIndex.ImportInfo importInfo,
            Set<String> expectedQualifiedNames,
            Set<String> visitedTypes
    ) {
        String qualifiedName = qualifiedNameOf(typeDeclaration, packageName);
        if (expectedQualifiedNames.contains(qualifiedName)) {
            return true;
        }
        if (!(typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration)) {
            return false;
        }
        for (ClassOrInterfaceType implementedType : classOrInterfaceDeclaration.getImplementedTypes()) {
            if (matchesQualifiedType(implementedType.toString(), packageName, importInfo, expectedQualifiedNames, visitedTypes)) {
                return true;
            }
        }
        for (ClassOrInterfaceType extendedType : classOrInterfaceDeclaration.getExtendedTypes()) {
            if (matchesQualifiedType(extendedType.toString(), packageName, importInfo, expectedQualifiedNames, visitedTypes)) {
                return true;
            }
        }
        return false;
    }

    private TypeReferenceResolutionContext typeReferenceResolutionContext(SourceUnit sourceUnit) {
        Optional<CompilationUnit> compilationUnit = sourceUnit.compilationUnit();
        if (compilationUnit.isEmpty()) {
            return new TypeReferenceResolutionContext("", Map.of(), Set.of());
        }
        Map<String, String> explicitImports = new LinkedHashMap<>();
        Set<String> wildcardImports = new LinkedHashSet<>();
        for (ImportDeclaration importDeclaration : compilationUnit.orElseThrow().getImports()) {
            if (importDeclaration.isStatic()) {
                continue;
            }
            String name = importDeclaration.getNameAsString();
            if (importDeclaration.isAsterisk()) {
                wildcardImports.add(name);
                continue;
            }
            int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < name.length() - 1) {
                explicitImports.putIfAbsent(name.substring(lastDot + 1), name);
            }
        }
        return new TypeReferenceResolutionContext(packageName(sourceUnit), Map.copyOf(explicitImports), Set.copyOf(wildcardImports));
    }

    private String packageName(SourceUnit sourceUnit) {
        return sourceUnit.compilationUnit()
                .flatMap(compilationUnit -> compilationUnit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()))
                .orElse("");
    }

    private String qualifiedNameOf(TypeDeclaration<?> typeDeclaration, String packageName) {
        List<String> segments = new ArrayList<>();
        segments.add(typeDeclaration.getNameAsString());
        Optional<com.github.javaparser.ast.Node> parent = typeDeclaration.getParentNode();
        while (parent.isPresent()) {
            com.github.javaparser.ast.Node node = parent.orElseThrow();
            if (node instanceof TypeDeclaration<?> parentType) {
                segments.add(parentType.getNameAsString());
            }
            parent = node.getParentNode();
        }
        java.util.Collections.reverse(segments);
        String localName = String.join(".", segments);
        return packageName.isBlank() ? localName : packageName + "." + localName;
    }

    private Set<String> directAnnotationNames(com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> node) {
        return node.getAnnotations().stream()
                .map(JavaSourceInspector::annotationSimpleName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeTypeReference(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "";
        }
        String normalized = rawType.trim();
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart);
        }
        while (normalized.endsWith("[]")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("...")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized.trim();
    }

    private boolean isVarArgs(MethodDeclaration method) {
        int parameterCount = method.getParameters().size();
        return parameterCount > 0 && method.getParameter(parameterCount - 1).isVarArgs();
    }

    private String visibility(MethodDeclaration method) {
        if (method.isPublic()) {
            return "public";
        }
        if (method.isProtected()) {
            return "protected";
        }
        if (method.isPrivate()) {
            return "private";
        }
        return "package";
    }

    private record TypeReferenceResolutionContext(String packageName, Map<String, String> explicitImports, Set<String> wildcardImports) {
    }
}
