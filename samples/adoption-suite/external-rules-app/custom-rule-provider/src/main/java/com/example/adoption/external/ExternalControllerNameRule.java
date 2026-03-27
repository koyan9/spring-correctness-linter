package com.example.adoption.external;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.koyan9.linter.core.JavaSourceInspector;
import io.github.koyan9.linter.core.LintIssue;
import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.LintSeverity;
import io.github.koyan9.linter.core.ProjectContext;
import io.github.koyan9.linter.core.RuleDomain;
import io.github.koyan9.linter.core.SourceUnit;
import io.github.koyan9.linter.core.SpringSemanticFacts;

import java.util.ArrayList;
import java.util.List;

public final class ExternalControllerNameRule implements LintRule {

    @Override
    public String id() {
        return "EXTERNAL_CONTROLLER_NAME_RULE";
    }

    @Override
    public String title() {
        return "Flag external controller method names";
    }

    @Override
    public String description() {
        return "Demonstrates service-loaded external lint rules through the Maven plugin classpath.";
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
        List<LintIssue> issues = new ArrayList<>();
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
