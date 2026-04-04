package com.scanner.app.orchestrator;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record StepExecutionResult(
        boolean success,
        boolean skipped,
        boolean fatal,
        boolean timedOut,
        String message,
        Set<String> discoveredUrls,
        Set<String> forms,
        Set<String> queryParameters,
        Set<String> jsonEndpoints
) {
    public StepExecutionResult {
        discoveredUrls = immutable(discoveredUrls);
        forms = immutable(forms);
        queryParameters = immutable(queryParameters);
        jsonEndpoints = immutable(jsonEndpoints);
    }

    public static StepExecutionResult success(String message) {
        return new StepExecutionResult(true, false, false, false, message, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static StepExecutionResult success(String message, Set<String> discoveredUrls, Set<String> forms, Set<String> queryParameters, Set<String> jsonEndpoints) {
        return new StepExecutionResult(true, false, false, false, message, discoveredUrls, forms, queryParameters, jsonEndpoints);
    }

    public static StepExecutionResult skipped(String message) {
        return new StepExecutionResult(true, true, false, false, message, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static StepExecutionResult nonFatalFailure(String message, boolean timedOut) {
        return new StepExecutionResult(false, false, false, timedOut, message, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static StepExecutionResult fatalFailure(String message) {
        return new StepExecutionResult(false, false, true, false, message, Set.of(), Set.of(), Set.of(), Set.of());
    }

    private static Set<String> immutable(Set<String> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(input));
    }
}
