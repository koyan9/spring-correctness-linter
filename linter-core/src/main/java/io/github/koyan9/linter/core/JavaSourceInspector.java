/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class JavaSourceInspector {

    private static final JavaParser PARSER = new JavaParser();

    private JavaSourceInspector() {
    }

    public static Optional<CompilationUnit> parseCompilationUnit(String content) {
        return inspect(content).compilationUnit();
    }

    public static ParseOutcome inspect(String content) {
        ParseResult<CompilationUnit> result = PARSER.parse(content);
        return new ParseOutcome(
                result.getResult(),
                result.getProblems().stream().map(problem -> problem.getMessage()).toList()
        );
    }

    public static SourceStructure buildStructure(Optional<CompilationUnit> compilationUnit) {
        if (compilationUnit.isEmpty()) {
            return SourceStructure.empty();
        }

        List<TypeDeclaration<?>> typeDeclarations = findTypeDeclarations(compilationUnit.get());
        List<MethodDeclaration> methods = new ArrayList<>();
        Map<TypeDeclaration<?>, List<MethodDeclaration>> methodsByType = new LinkedHashMap<>();
        for (TypeDeclaration<?> typeDeclaration : typeDeclarations) {
            List<MethodDeclaration> typeMethods = List.copyOf(findMethods(typeDeclaration));
            methodsByType.put(typeDeclaration, typeMethods);
            methods.addAll(typeMethods);
        }
        return new SourceStructure(typeDeclarations, methods, methodsByType);
    }

    public static boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return findAnnotation(node, simpleName).isPresent();
    }

    public static boolean hasAnnotation(NodeWithAnnotations<?> node, ProjectContext context, String simpleName) {
        if (hasAnnotation(node, simpleName)) {
            return true;
        }
        return context.annotationMetadataIndex().expandedAnnotationNames(node).contains(simpleName);
    }

    public static long annotationMatchCount(NodeWithAnnotations<?> node, ProjectContext context, String simpleName) {
        return node.getAnnotations().stream()
                .filter(annotationExpr -> context.annotationMetadataIndex().expandedAnnotationNames(annotationExpr).contains(simpleName))
                .count();
    }

    public static Optional<AnnotationExpr> findAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream()
                .filter(annotation -> annotationName(annotation).equals(simpleName))
                .findFirst();
    }

    public static boolean annotationDeclaresMember(NodeWithAnnotations<?> node, String annotationName, String memberName) {
        return findAnnotation(node, annotationName)
                .map(annotation -> annotationDeclaresMember(annotation, memberName))
                .orElse(false);
    }

    public static Optional<String> annotationMemberValue(NodeWithAnnotations<?> node, ProjectContext context, String annotationName, String memberName) {
        for (AnnotationExpr annotationExpr : node.getAnnotations()) {
            String currentName = annotationSimpleName(annotationExpr);
            if (currentName.equals(annotationName)) {
                Optional<String> directValue = annotationMemberValue(annotationExpr, memberName);
                if (directValue.isPresent()) {
                    return directValue;
                }
            }
        }
        return context.annotationMetadataIndex().annotationMemberValue(node, annotationName, memberName);
    }

    public static boolean annotationDeclaresMember(NodeWithAnnotations<?> node, ProjectContext context, String annotationName, String memberName) {
        if (annotationDeclaresMember(node, annotationName, memberName)) {
            return true;
        }
        return context.annotationMetadataIndex().annotationMemberValue(node, annotationName, memberName).isPresent();
    }

    public static boolean annotationDeclaresMember(AnnotationExpr annotationExpr, String memberName) {
        if (annotationExpr instanceof NormalAnnotationExpr normalAnnotationExpr) {
            return normalAnnotationExpr.getPairs().stream().anyMatch(pair -> pair.getNameAsString().equals(memberName));
        }
        return false;
    }

    public static boolean annotationMemberContains(NodeWithAnnotations<?> node, String annotationName, String memberName, String token) {
        return findAnnotation(node, annotationName)
                .flatMap(annotation -> annotationMemberValue(annotation, memberName))
                .map(value -> value.contains(token))
                .orElse(false);
    }

    public static boolean annotationMemberContains(NodeWithAnnotations<?> node, ProjectContext context, String annotationName, String memberName, String token) {
        if (annotationMemberContains(node, annotationName, memberName, token)) {
            return true;
        }
        return context.annotationMetadataIndex().annotationMemberValue(node, annotationName, memberName)
                .map(value -> value.contains(token))
                .orElse(false);
    }

    public static Optional<String> annotationMemberValue(AnnotationExpr annotationExpr, String memberName) {
        if (annotationExpr instanceof NormalAnnotationExpr normalAnnotationExpr) {
            return normalAnnotationExpr.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals(memberName))
                    .map(pair -> expressionText(pair.getValue()))
                    .findFirst();
        }
        if (annotationExpr instanceof SingleMemberAnnotationExpr singleMemberAnnotationExpr && "value".equals(memberName)) {
            return Optional.of(expressionText(singleMemberAnnotationExpr.getMemberValue()));
        }
        return Optional.empty();
    }

    public static Set<String> annotationNames(NodeWithAnnotations<?> node) {
        Set<String> names = new LinkedHashSet<>();
        for (AnnotationExpr annotation : node.getAnnotations()) {
            names.add(annotationName(annotation));
        }
        return names;
    }

    public static Set<String> annotationNames(NodeWithAnnotations<?> node, ProjectContext context) {
        Set<String> names = new LinkedHashSet<>(annotationNames(node));
        names.addAll(context.annotationMetadataIndex().expandedAnnotationNames(node));
        return names;
    }

    public static List<TypeDeclaration<?>> findTypeDeclarations(CompilationUnit compilationUnit) {
        List<TypeDeclaration<?>> declarations = new ArrayList<>();
        declarations.addAll(compilationUnit.findAll(ClassOrInterfaceDeclaration.class));
        declarations.addAll(compilationUnit.findAll(RecordDeclaration.class));
        declarations.addAll(compilationUnit.findAll(EnumDeclaration.class));
        return declarations;
    }

    public static List<MethodDeclaration> findMethods(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.getMethods();
        }
        if (typeDeclaration instanceof RecordDeclaration recordDeclaration) {
            return recordDeclaration.getMethods();
        }
        if (typeDeclaration instanceof EnumDeclaration enumDeclaration) {
            return enumDeclaration.getMethods();
        }
        return List.of();
    }

    public static boolean isController(TypeDeclaration<?> typeDeclaration) {
        return hasAnnotation(typeDeclaration, "RestController") || hasAnnotation(typeDeclaration, "Controller");
    }

    public static boolean isController(TypeDeclaration<?> typeDeclaration, ProjectContext context) {
        return hasAnnotation(typeDeclaration, context, "RestController") || hasAnnotation(typeDeclaration, context, "Controller");
    }

    public static boolean isRequestMapping(MethodDeclaration methodDeclaration) {
        return hasAnnotation(methodDeclaration, "GetMapping")
                || hasAnnotation(methodDeclaration, "PostMapping")
                || hasAnnotation(methodDeclaration, "PutMapping")
                || hasAnnotation(methodDeclaration, "DeleteMapping")
                || hasAnnotation(methodDeclaration, "PatchMapping")
                || hasAnnotation(methodDeclaration, "RequestMapping");
    }

    public static boolean isRequestMapping(MethodDeclaration methodDeclaration, ProjectContext context) {
        return hasAnnotation(methodDeclaration, context, "GetMapping")
                || hasAnnotation(methodDeclaration, context, "PostMapping")
                || hasAnnotation(methodDeclaration, context, "PutMapping")
                || hasAnnotation(methodDeclaration, context, "DeleteMapping")
                || hasAnnotation(methodDeclaration, context, "PatchMapping")
                || hasAnnotation(methodDeclaration, context, "RequestMapping");
    }

    public static boolean isInitializationCallback(TypeDeclaration<?> typeDeclaration, MethodDeclaration methodDeclaration, ProjectContext context) {
        return hasAnnotation(methodDeclaration, context, "PostConstruct")
                || isInitializingBeanCallback(typeDeclaration, methodDeclaration);
    }

    public static boolean isStartupLifecycleMethod(TypeDeclaration<?> typeDeclaration, MethodDeclaration methodDeclaration, ProjectContext context) {
        return isInitializationCallback(typeDeclaration, methodDeclaration, context)
                || isApplicationRunnerCallback(typeDeclaration, methodDeclaration)
                || isCommandLineRunnerCallback(typeDeclaration, methodDeclaration)
                || isSmartInitializingSingletonCallback(typeDeclaration, methodDeclaration);
    }

    public static boolean isInitializingBeanCallback(TypeDeclaration<?> typeDeclaration, MethodDeclaration methodDeclaration) {
        if (!"afterPropertiesSet".equals(methodDeclaration.getNameAsString())) {
            return false;
        }
        if (!methodDeclaration.getParameters().isEmpty() || !methodDeclaration.getType().isVoidType()) {
            return false;
        }
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.getImplementedTypes().stream()
                    .map(type -> type.getName().getIdentifier())
                    .anyMatch(name -> name.equals("InitializingBean"));
        }
        return false;
    }

    public static boolean isApplicationRunnerCallback(TypeDeclaration<?> typeDeclaration, MethodDeclaration methodDeclaration) {
        if (!"run".equals(methodDeclaration.getNameAsString()) || methodDeclaration.getParameters().size() != 1) {
            return false;
        }
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.getImplementedTypes().stream()
                    .map(type -> type.getName().getIdentifier())
                    .anyMatch(name -> name.equals("ApplicationRunner"));
        }
        return false;
    }

    public static boolean isCommandLineRunnerCallback(TypeDeclaration<?> typeDeclaration, MethodDeclaration methodDeclaration) {
        if (!"run".equals(methodDeclaration.getNameAsString()) || methodDeclaration.getParameters().size() != 1) {
            return false;
        }
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.getImplementedTypes().stream()
                    .map(type -> type.getName().getIdentifier())
                    .anyMatch(name -> name.equals("CommandLineRunner"));
        }
        return false;
    }

    public static boolean isSmartInitializingSingletonCallback(TypeDeclaration<?> typeDeclaration, MethodDeclaration methodDeclaration) {
        if (!"afterSingletonsInstantiated".equals(methodDeclaration.getNameAsString())
                || !methodDeclaration.getParameters().isEmpty()
                || !methodDeclaration.getType().isVoidType()) {
            return false;
        }
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.getImplementedTypes().stream()
                    .map(type -> type.getName().getIdentifier())
                    .anyMatch(name -> name.equals("SmartInitializingSingleton"));
        }
        return false;
    }

    public static int lineOf(Node node) {
        return node.getBegin().map(position -> position.line).orElse(1);
    }

    static String annotationSimpleName(AnnotationExpr annotationExpr) {
        String identifier = annotationExpr.getName().getIdentifier();
        return identifier == null || identifier.isBlank() ? annotationExpr.getNameAsString() : identifier;
    }

    static Map<String, String> annotationMembers(AnnotationExpr annotationExpr) {
        if (annotationExpr instanceof NormalAnnotationExpr normalAnnotationExpr) {
            Map<String, String> members = new LinkedHashMap<>();
            normalAnnotationExpr.getPairs().forEach(pair -> members.put(pair.getNameAsString(), expressionText(pair.getValue())));
            return members;
        }
        if (annotationExpr instanceof SingleMemberAnnotationExpr singleMemberAnnotationExpr) {
            return Map.of("value", expressionText(singleMemberAnnotationExpr.getMemberValue()));
        }
        return Map.of();
    }

    private static String annotationName(AnnotationExpr annotationExpr) {
        return annotationSimpleName(annotationExpr);
    }

    private static String expressionText(Expression expression) {
        return expression.toString();
    }

    public record ParseOutcome(Optional<CompilationUnit> compilationUnit, List<String> problems) {

        public ParseOutcome {
            problems = List.copyOf(problems);
        }
    }
}
