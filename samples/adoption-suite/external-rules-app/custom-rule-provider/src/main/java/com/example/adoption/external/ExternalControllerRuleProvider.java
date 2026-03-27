package com.example.adoption.external;

import io.github.koyan9.linter.core.LintRule;
import io.github.koyan9.linter.core.spi.LintRuleProvider;

import java.util.List;

public final class ExternalControllerRuleProvider implements LintRuleProvider {

    @Override
    public List<LintRule> rules() {
        return List.of(new ExternalControllerNameRule());
    }
}
