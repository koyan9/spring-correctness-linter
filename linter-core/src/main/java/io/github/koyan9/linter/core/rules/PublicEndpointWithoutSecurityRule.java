/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.SourceUnit;

import java.util.ArrayList;
import java.util.List;

public final class PublicEndpointWithoutSecurityRule extends AbstractSpringRule {

    @Override
    public String id() {
        return "SPRING_ENDPOINT_SECURITY";
    }

    @Override
    public String title() {
        return "Flag public endpoints without security annotations";
    }

    @Override
    public String description() {
        return "Public controller methods should have explicit security intent at class or method level.";
    }

    @Override
    public List<LintIssue> evaluate(SourceUnit sourceUnit, ProjectContext context) {
        List<LintIssue> issues = new ArrayList<>();
        sourceUnit.compilationUnit().ifPresent(compilationUnit -> collectIssues(sourceUnit, compilationUnit, issues));
        return issues;
    }

    private void collectIssues(SourceUnit sourceUnit, CompilationUnit compilationUnit, List<LintIssue> issues) {
        for (TypeDeclaration<?> typeDeclaration : JavaSourceInspector.findTypeDeclarations(compilationUnit)) {
            if (!JavaSourceInspector.isController(typeDeclaration)) {
                continue;
            }

            boolean classSecured = hasSecurityAnnotation(JavaSourceInspector.annotationNames(typeDeclaration));
            for (MethodDeclaration method : JavaSourceInspector.findMethods(typeDeclaration)) {
                boolean mapping = JavaSourceInspector.isRequestMapping(method);
                boolean methodSecured = hasSecurityAnnotation(JavaSourceInspector.annotationNames(method));
                if (mapping && method.isPublic() && !classSecured && !methodSecured) {
                    issues.add(issue(sourceUnit, JavaSourceInspector.lineOf(method), "Endpoint method '" + method.getNameAsString() + "' is public and mapped but has no explicit security annotation."));
                }
            }
        }
    }
}