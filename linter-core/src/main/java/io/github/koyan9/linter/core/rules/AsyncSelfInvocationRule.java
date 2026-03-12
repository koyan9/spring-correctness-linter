/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
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

public final class AsyncSelfInvocationRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_ASYNC_SELF_INVOCATION";
    }

    @Override
    public String title() {
        return "Detect async self invocation";
    }

    @Override
    public String description() {
        return "Calling a same-type or inherited @Async method directly bypasses Spring async proxies.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.ASYNC;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method directly invokes this.asyncMethod() or asyncMethod() where the matching same-type target is annotated with @Async or inherits a class-level @Async boundary.",
                "The application relies on Spring async proxy advice rather than manual executor scheduling."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Projects using AspectJ weaving or explicit executor submission may intentionally allow this pattern.",
                "The rule matches same-type calls by method name and argument count and supports varargs, but does not fully resolve imported-static edge cases.",
                "Class-level @Async is treated as applying to public methods, and type resolution is best-effort without a full symbol solver.",
                "Proxy-injection patterns such as self-injection or ApplicationContext lookups are treated as external calls unless there is an explicit self-call."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Move the async method into another bean and invoke it through Spring-managed wiring.",
                "If self-invocation is intentional, inject the proxied bean explicitly and document the design choice."
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
            Set<MethodSignature> exactAsyncMethods = new LinkedHashSet<>();
            Set<MethodSignature> exactNonAsyncMethods = new LinkedHashSet<>();
            Set<VarArgsSignature> varArgsAsyncMethods = new LinkedHashSet<>();

            for (TypeResolutionIndex.TypeDescriptor relatedType : typeIndex.relatedTypes(rootType)) {
                boolean classAsync = facts.typeFacts(relatedType.declaration()).hasAsyncBoundary();
                SourceStructure structure = relatedType.structure();
                for (MethodDeclaration method : structure.methodsOf(relatedType.declaration())) {
                    boolean async = facts.methodFacts(relatedType.declaration(), method).hasAsyncBoundary()
                            || (classAsync && method.isPublic());
                    if (isVarArgs(method)) {
                        if (async) {
                            varArgsAsyncMethods.add(new VarArgsSignature(method.getNameAsString(), method.getParameters().size()));
                        }
                        continue;
                    }
                    MethodSignature signature = new MethodSignature(method.getNameAsString(), method.getParameters().size());
                    if (async) {
                        exactAsyncMethods.add(signature);
                    } else {
                        exactNonAsyncMethods.add(signature);
                    }
                }
            }

            if (exactAsyncMethods.isEmpty() && varArgsAsyncMethods.isEmpty()) {
                continue;
            }

            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                for (MethodCallExpr methodCall : method.findAll(MethodCallExpr.class)) {
                    if (isAsyncSelfInvocation(
                            typeDeclaration,
                            method,
                            methodCall,
                            exactAsyncMethods,
                            exactNonAsyncMethods,
                            varArgsAsyncMethods
                    )) {
                        issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(methodCall), "Method '" + methodCall.getNameAsString() + "' is @Async and invoked via self-call; async advice will not run."));
                    }
                }
            }
        }
    }

    private boolean isAsyncSelfInvocation(
            TypeDeclaration<?> typeDeclaration,
            MethodDeclaration enclosingMethod,
            MethodCallExpr methodCall,
            Set<MethodSignature> exactAsyncMethods,
            Set<MethodSignature> exactNonAsyncMethods,
            Set<VarArgsSignature> varArgsAsyncMethods
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
        return matchesAsyncTarget(methodCall, exactAsyncMethods, exactNonAsyncMethods, varArgsAsyncMethods);
    }

    private boolean belongsToCurrentType(TypeDeclaration<?> typeDeclaration, MethodCallExpr methodCall) {
        return methodCall.findAncestor(TypeDeclaration.class)
                .map(typeDeclaration::equals)
                .orElse(false);
    }

    private boolean isDirectSameTypeCall(MethodCallExpr methodCall) {
        return methodCall.getScope().isEmpty()
                || methodCall.getScope().filter(ThisExpr.class::isInstance).isPresent();
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

    private boolean matchesAsyncTarget(
            MethodCallExpr methodCall,
            Set<MethodSignature> exactAsyncMethods,
            Set<MethodSignature> exactNonAsyncMethods,
            Set<VarArgsSignature> varArgsAsyncMethods
    ) {
        MethodSignature candidate = new MethodSignature(methodCall.getNameAsString(), methodCall.getArguments().size());
        if (exactAsyncMethods.contains(candidate)) {
            return true;
        }
        if (exactNonAsyncMethods.contains(candidate)) {
            return false;
        }
        for (VarArgsSignature target : varArgsAsyncMethods) {
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
