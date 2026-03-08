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

    public static Optional<String> annotationMemberValue(AnnotationExpr annotationExpr, String memberName) {
        if (annotationExpr instanceof NormalAnnotationExpr normalAnnotationExpr) {
            return normalAnnotationExpr.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals(memberName))
                    .map(pair -> expressionText(pair.getValue()))
                    .findFirst();
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

    public static boolean isRequestMapping(MethodDeclaration methodDeclaration) {
        return hasAnnotation(methodDeclaration, "GetMapping")
                || hasAnnotation(methodDeclaration, "PostMapping")
                || hasAnnotation(methodDeclaration, "PutMapping")
                || hasAnnotation(methodDeclaration, "DeleteMapping")
                || hasAnnotation(methodDeclaration, "PatchMapping")
                || hasAnnotation(methodDeclaration, "RequestMapping");
    }

    public static int lineOf(Node node) {
        return node.getBegin().map(position -> position.line).orElse(1);
    }

    private static String annotationName(AnnotationExpr annotationExpr) {
        String identifier = annotationExpr.getName().getIdentifier();
        return identifier == null || identifier.isBlank() ? annotationExpr.getNameAsString() : identifier;
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
