/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.SourceStructure;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;
import io.github.koyan9.linter.core.TypeResolutionIndex;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TransactionalSelfInvocationRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_TX_SELF_INVOCATION";
    }

    @Override
    public String title() {
        return "Detect transactional self invocation";
    }

    @Override
    public String description() {
        return "Calling a same-type or inherited transactional method directly, including this.method() or method(), bypasses Spring proxies.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.TRANSACTION;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method directly invokes `this.someMethod()` or `someMethod()` where the matching same-type target is transactional by method annotation or class-level `@Transactional` on the current or inherited type.",
                "The application relies on proxy-based transaction advice rather than direct bytecode weaving."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Projects using AspectJ weaving or another non-proxy transaction mechanism may intentionally allow this pattern.",
                "The rule matches same-type calls by method name and argument count and supports varargs, but does not fully resolve imported-static edge cases.",
                "Class-level `@Transactional` is treated as applying to public methods, and type resolution is best-effort without a full symbol solver; custom proxy settings or weaving can change that boundary.",
                "Proxy-injection patterns such as self-injection or ApplicationContext lookups are treated as external calls unless there is an explicit self-call.",
                "Method references are only checked for direct `this::method` or `ClassName::method` usage inside the same type."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Move the transactional method into another bean and invoke it through Spring-managed wiring.",
                "If self-invocation is intentional, inject the proxied bean explicitly and document the design choice.",
                "If the method is reached via a proxy lookup (for example, through an injected self proxy or ApplicationContext), document that pattern so it is clear why the rule does not apply."
        );
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        collectIssues(sourceUnit, context, issues);
        return issues;
    }

    private void collectIssues(SourceUnit sourceUnit, ProjectContext context, List<LintIssue> issues) {
        SpringSemanticFacts facts = context.springFacts(sourceUnit);
        TypeResolutionIndex typeIndex = context.typeResolutionIndex();
        for (TypeDeclaration<?> typeDeclaration : sourceUnit.structure().typeDeclarations()) {
            TypeResolutionIndex.TypeDescriptor rootType = typeIndex.descriptorFor(typeDeclaration, sourceUnit);
            Set<MethodSignature> exactTransactionalMethods = new LinkedHashSet<>();
            Set<MethodSignature> exactNonTransactionalMethods = new LinkedHashSet<>();
            Set<VarArgsSignature> varArgsTransactionalMethods = new LinkedHashSet<>();
            Set<MethodSignature> exactFinalTransactionalMethods = new LinkedHashSet<>();
            Set<VarArgsSignature> varArgsFinalTransactionalMethods = new LinkedHashSet<>();
            Set<String> transactionalMethodNames = new LinkedHashSet<>();
            Set<String> nonTransactionalMethodNames = new LinkedHashSet<>();

            for (TypeResolutionIndex.TypeDescriptor relatedType : typeIndex.relatedTypes(rootType)) {
                boolean classTransactional = facts.typeFacts(relatedType.declaration()).hasTransactionalBoundary();
                SourceStructure structure = relatedType.structure();
                for (MethodDeclaration method : structure.methodsOf(relatedType.declaration())) {
                    boolean transactional = facts.methodFacts(relatedType.declaration(), method).hasTransactionalBoundary()
                            || (classTransactional && method.isPublic());
                    if (isVarArgs(method)) {
                        if (transactional) {
                            varArgsTransactionalMethods.add(new VarArgsSignature(method.getNameAsString(), method.getParameters().size()));
                            if (method.isFinal()) {
                                varArgsFinalTransactionalMethods.add(new VarArgsSignature(method.getNameAsString(), method.getParameters().size()));
                            }
                        }
                        continue;
                    }
                    MethodSignature signature = new MethodSignature(method.getNameAsString(), method.getParameters().size());
                    if (transactional) {
                        exactTransactionalMethods.add(signature);
                        transactionalMethodNames.add(method.getNameAsString());
                        if (method.isFinal()) {
                            exactFinalTransactionalMethods.add(signature);
                        }
                    } else {
                        exactNonTransactionalMethods.add(signature);
                        nonTransactionalMethodNames.add(method.getNameAsString());
                    }
                }
            }

            if (exactTransactionalMethods.isEmpty() && varArgsTransactionalMethods.isEmpty()) {
                continue;
            }

            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                for (MethodCallExpr methodCall : method.findAll(MethodCallExpr.class)) {
                    if (isTransactionalSelfInvocation(
                            typeDeclaration,
                            method,
                            methodCall,
                            exactTransactionalMethods,
                            exactNonTransactionalMethods,
                            varArgsTransactionalMethods,
                            exactFinalTransactionalMethods,
                            varArgsFinalTransactionalMethods
                    )) {
                        issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(methodCall), "Method '" + methodCall.getNameAsString() + "' is @Transactional and invoked via self-call; proxy advice will not run."));
                    }
                }
                for (MethodReferenceExpr methodReference : method.findAll(MethodReferenceExpr.class)) {
                    if (isTransactionalSelfReference(typeDeclaration, methodReference, transactionalMethodNames, nonTransactionalMethodNames)) {
                        issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(methodReference), "Method reference '" + methodReference.getIdentifier() + "' targets a @Transactional method via self-reference; proxy advice will not run."));
                    }
                }
            }
        }
    }

    private boolean isTransactionalSelfInvocation(
            TypeDeclaration<?> typeDeclaration,
            MethodDeclaration enclosingMethod,
            MethodCallExpr methodCall,
            Set<MethodSignature> exactTransactionalMethods,
            Set<MethodSignature> exactNonTransactionalMethods,
            Set<VarArgsSignature> varArgsTransactionalMethods,
            Set<MethodSignature> exactFinalTransactionalMethods,
            Set<VarArgsSignature> varArgsFinalTransactionalMethods
    ) {
        if (!belongsToCurrentType(typeDeclaration, methodCall)) {
            return false;
        }
        if (!isDirectSameTypeCall(methodCall)) {
            return false;
        }
        if (isDirectRecursiveCall(enclosingMethod, methodCall)) {
            return false;
        }
        return matchesTransactionalTarget(
                methodCall,
                exactTransactionalMethods,
                exactNonTransactionalMethods,
                varArgsTransactionalMethods,
                exactFinalTransactionalMethods,
                varArgsFinalTransactionalMethods
        );
    }

    private boolean belongsToCurrentType(TypeDeclaration<?> typeDeclaration, MethodCallExpr methodCall) {
        return methodCall.findAncestor(TypeDeclaration.class)
                .map(typeDeclaration::equals)
                .orElse(false);
    }

    private boolean belongsToCurrentType(TypeDeclaration<?> typeDeclaration, MethodReferenceExpr methodReference) {
        return methodReference.findAncestor(TypeDeclaration.class)
                .map(typeDeclaration::equals)
                .orElse(false);
    }

    private boolean isDirectSameTypeCall(MethodCallExpr methodCall) {
        return methodCall.getScope().isEmpty()
                || methodCall.getScope().filter(ThisExpr.class::isInstance).isPresent();
    }

    private boolean isTransactionalSelfReference(
            TypeDeclaration<?> typeDeclaration,
            MethodReferenceExpr methodReference,
            Set<String> transactionalMethodNames,
            Set<String> nonTransactionalMethodNames
    ) {
        if (!belongsToCurrentType(typeDeclaration, methodReference)) {
            return false;
        }
        String identifier = methodReference.getIdentifier();
        if ("new".equals(identifier)) {
            return false;
        }
        if (!isDirectSelfReference(typeDeclaration, methodReference)) {
            return false;
        }
        if (nonTransactionalMethodNames.contains(identifier)) {
            return false;
        }
        return transactionalMethodNames.contains(identifier);
    }

    private boolean isDirectSelfReference(TypeDeclaration<?> typeDeclaration, MethodReferenceExpr methodReference) {
        if (methodReference.getScope() instanceof ThisExpr) {
            return true;
        }
        return methodReference.getScope().toString().equals(typeDeclaration.getNameAsString());
    }

    private boolean isDirectRecursiveCall(MethodDeclaration enclosingMethod, MethodCallExpr methodCall) {
        if (!enclosingMethod.getNameAsString().equals(methodCall.getNameAsString())) {
            return false;
        }
        int argumentCount = methodCall.getArguments().size();
        int parameterCount = enclosingMethod.getParameters().size();
        if (isVarArgs(enclosingMethod)) {
            return argumentCount >= Math.max(0, parameterCount - 1);
        }
        return argumentCount == parameterCount;
    }

    private boolean isVarArgs(MethodDeclaration method) {
        int parameterCount = method.getParameters().size();
        if (parameterCount == 0) {
            return false;
        }
        return method.getParameter(parameterCount - 1).isVarArgs();
    }

    private boolean matchesTransactionalTarget(
            MethodCallExpr methodCall,
            Set<MethodSignature> exactTransactionalMethods,
            Set<MethodSignature> exactNonTransactionalMethods,
            Set<VarArgsSignature> varArgsTransactionalMethods,
            Set<MethodSignature> exactFinalTransactionalMethods,
            Set<VarArgsSignature> varArgsFinalTransactionalMethods
    ) {
        MethodSignature candidate = new MethodSignature(methodCall.getNameAsString(), methodCall.getArguments().size());
        if (exactFinalTransactionalMethods.contains(candidate)) {
            return false;
        }
        for (VarArgsSignature finalTarget : varArgsFinalTransactionalMethods) {
            if (!finalTarget.name().equals(candidate.name())) {
                continue;
            }
            int minimumArguments = Math.max(0, finalTarget.arity() - 1);
            if (candidate.arity() >= minimumArguments) {
                return false;
            }
        }
        if (exactTransactionalMethods.contains(candidate)) {
            return true;
        }
        if (exactNonTransactionalMethods.contains(candidate)) {
            return false;
        }
        for (VarArgsSignature target : varArgsTransactionalMethods) {
            if (!target.name().equals(candidate.name())) {
                continue;
            }
            int minimumArguments = Math.max(0, target.arity() - 1);
            if (candidate.arity() >= minimumArguments) {
                return true;
            }
        }
        return false;
    }

    private record MethodSignature(String name, int arity) {
    }

    private record VarArgsSignature(String name, int arity) {
    }
}
