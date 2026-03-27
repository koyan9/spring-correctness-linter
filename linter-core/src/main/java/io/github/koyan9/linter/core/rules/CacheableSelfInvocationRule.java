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

public final class CacheableSelfInvocationRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_CACHEABLE_SELF_INVOCATION";
    }

    @Override
    public String title() {
        return "Detect @Cacheable self invocation";
    }

    @Override
    public String description() {
        return "Calling a same-type or inherited @Cacheable method directly bypasses Spring cache proxies.";
    }

    @Override
    public RuleDomain domain() {
        return RuleDomain.CACHE;
    }

    @Override
    public List<String> appliesWhen() {
        return List.of(
                "A method directly invokes `this.cachedMethod()` or `cachedMethod()` where the matching same-type target uses `@Cacheable` semantics.",
                "The application relies on Spring proxy-based cache interception rather than direct cache APIs or weaving."
        );
    }

    @Override
    public List<String> commonFalsePositiveBoundaries() {
        return List.of(
                "Projects using AspectJ weaving or direct `CacheManager` interactions may intentionally allow this pattern.",
                "The rule matches same-type calls by method name and argument count and supports varargs, but does not fully resolve imported-static edge cases.",
                "Proxy-injection patterns such as self-injection or ApplicationContext lookups are treated as external calls unless there is an explicit self-call.",
                "Method references are only checked for direct `this::method` or `ClassName::method` usage inside the same type."
        );
    }

    @Override
    public List<String> recommendedFixes() {
        return List.of(
                "Move the cached method into another bean and invoke it through Spring-managed wiring.",
                "If same-bean cache access is intentional, inject the proxied bean explicitly and document the design choice.",
                "Use direct cache APIs when the goal is explicit in-method cache coordination rather than annotation-driven interception."
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
            Set<MethodSignature> exactCacheableMethods = new LinkedHashSet<>();
            Set<MethodSignature> exactNonCacheableMethods = new LinkedHashSet<>();
            Set<VarArgsSignature> varArgsCacheableMethods = new LinkedHashSet<>();
            Set<String> cacheableMethodNames = new LinkedHashSet<>();
            Set<String> nonCacheableMethodNames = new LinkedHashSet<>();

            for (TypeResolutionIndex.TypeDescriptor relatedType : typeIndex.relatedTypes(rootType)) {
                SourceStructure structure = relatedType.structure();
                for (MethodDeclaration method : structure.methodsOf(relatedType.declaration())) {
                    boolean cacheable = facts.methodFacts(relatedType.declaration(), method).hasCacheableOperation();
                    if (isVarArgs(method)) {
                        if (cacheable) {
                            varArgsCacheableMethods.add(new VarArgsSignature(method.getNameAsString(), method.getParameters().size()));
                        }
                        continue;
                    }
                    MethodSignature signature = new MethodSignature(method.getNameAsString(), method.getParameters().size());
                    if (cacheable) {
                        exactCacheableMethods.add(signature);
                        cacheableMethodNames.add(method.getNameAsString());
                    } else {
                        exactNonCacheableMethods.add(signature);
                        nonCacheableMethodNames.add(method.getNameAsString());
                    }
                }
            }

            if (exactCacheableMethods.isEmpty() && varArgsCacheableMethods.isEmpty()) {
                continue;
            }

            for (MethodDeclaration method : sourceUnit.structure().methodsOf(typeDeclaration)) {
                for (MethodCallExpr methodCall : method.findAll(MethodCallExpr.class)) {
                    if (isCacheableSelfInvocation(
                            typeDeclaration,
                            method,
                            methodCall,
                            exactCacheableMethods,
                            exactNonCacheableMethods,
                            varArgsCacheableMethods
                    )) {
                        issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(methodCall), "Method '" + methodCall.getNameAsString() + "' uses @Cacheable and is invoked via self-call; cache interception will not run."));
                    }
                }
                for (MethodReferenceExpr methodReference : method.findAll(MethodReferenceExpr.class)) {
                    if (isCacheableSelfReference(typeDeclaration, methodReference, cacheableMethodNames, nonCacheableMethodNames)) {
                        issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(methodReference), "Method reference '" + methodReference.getIdentifier() + "' targets an @Cacheable method via self-reference; cache interception will not run."));
                    }
                }
            }
        }
    }

    private boolean isCacheableSelfInvocation(
            TypeDeclaration<?> typeDeclaration,
            MethodDeclaration enclosingMethod,
            MethodCallExpr methodCall,
            Set<MethodSignature> exactCacheableMethods,
            Set<MethodSignature> exactNonCacheableMethods,
            Set<VarArgsSignature> varArgsCacheableMethods
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
        return matchesCacheableTarget(methodCall, exactCacheableMethods, exactNonCacheableMethods, varArgsCacheableMethods);
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

    private boolean isCacheableSelfReference(
            TypeDeclaration<?> typeDeclaration,
            MethodReferenceExpr methodReference,
            Set<String> cacheableMethodNames,
            Set<String> nonCacheableMethodNames
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
        if (nonCacheableMethodNames.contains(identifier)) {
            return false;
        }
        return cacheableMethodNames.contains(identifier);
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

    private boolean matchesCacheableTarget(
            MethodCallExpr methodCall,
            Set<MethodSignature> exactCacheableMethods,
            Set<MethodSignature> exactNonCacheableMethods,
            Set<VarArgsSignature> varArgsCacheableMethods
    ) {
        MethodSignature candidate = new MethodSignature(methodCall.getNameAsString(), methodCall.getArguments().size());
        if (exactCacheableMethods.contains(candidate)) {
            return true;
        }
        if (exactNonCacheableMethods.contains(candidate)) {
            return false;
        }
        for (VarArgsSignature target : varArgsCacheableMethods) {
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
