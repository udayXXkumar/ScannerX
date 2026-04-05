package com.scanner.app.orchestrator;

import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class ScanExecutionContext {
    private String normalizedTargetUrl;
    private String zapBaseUrl;
    private String zapApiKey;
    private Integer zapPort;
    private String zapWorkingDirectory;
    private Set<String> discoveredUrls = new LinkedHashSet<>();
    private Set<String> forms = new LinkedHashSet<>();
    private Set<String> queryParameters = new LinkedHashSet<>();
    private Set<String> jsonEndpoints = new LinkedHashSet<>();
    private List<String> skippedSteps = new ArrayList<>();
    private List<Integer> completedStages = new ArrayList<>();
    private Set<String> dedupeFingerprints = new LinkedHashSet<>();
    private volatile Long lastForwardProgressAt;
    private volatile Integer batchCompleted;
    private volatile Integer batchTotal;
    private volatile Integer stageProgressPercent;
    private volatile Long baseTierBudgetMs;
    private volatile Long borrowedBudgetMs;
    private volatile Long extendedTierBudgetMs;
    private volatile Boolean adaptiveBudgetActive;
    private volatile String currentStepLabel;

    public void merge(StepExecutionResult result) {
        if (result == null) {
            return;
        }
        int discoveredCount = discoveredUrls.size();
        int formCount = forms.size();
        int queryCount = queryParameters.size();
        int jsonCount = jsonEndpoints.size();
        discoveredUrls.addAll(result.discoveredUrls());
        forms.addAll(result.forms());
        queryParameters.addAll(result.queryParameters());
        jsonEndpoints.addAll(result.jsonEndpoints());
        if (discoveredUrls.size() > discoveredCount
                || forms.size() > formCount
                || queryParameters.size() > queryCount
                || jsonEndpoints.size() > jsonCount) {
            markForwardProgress();
        }
    }

    public boolean hasDiscoveredScope() {
        return !discoveredUrls.isEmpty() || !forms.isEmpty() || !queryParameters.isEmpty() || !jsonEndpoints.isEmpty();
    }

    public boolean hasInjectableInputs() {
        return !forms.isEmpty() || !queryParameters.isEmpty() || !jsonEndpoints.isEmpty();
    }

    public void addCompletedStage(int stageOrder) {
        if (!completedStages.contains(stageOrder)) {
            completedStages.add(stageOrder);
        }
    }

    public synchronized void markForwardProgress() {
        lastForwardProgressAt = System.currentTimeMillis();
    }

    public synchronized boolean hasRecentForwardProgress(Duration window) {
        if (window == null || lastForwardProgressAt == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastForwardProgressAt) <= window.toMillis();
    }

    public synchronized void beginStep(String stepLabel) {
        currentStepLabel = stepLabel;
        batchCompleted = null;
        batchTotal = null;
        stageProgressPercent = null;
        markForwardProgress();
    }

    public synchronized void updateBatchProgress(int completed, int total) {
        batchCompleted = Math.max(completed, 0);
        batchTotal = Math.max(total, 0);
        if (batchTotal != null && batchTotal > 0) {
            stageProgressPercent = Math.min(100, (int) Math.round((batchCompleted * 100.0) / batchTotal));
        } else {
            stageProgressPercent = null;
        }
        markForwardProgress();
    }

    public synchronized void updateStageProgressPercent(int percent, boolean markProgress) {
        stageProgressPercent = Math.max(0, Math.min(100, percent));
        if (markProgress) {
            markForwardProgress();
        }
    }

    public synchronized void initializeBudget(Duration baseBudget) {
        baseTierBudgetMs = baseBudget == null ? null : Math.max(baseBudget.toMillis(), 0L);
        borrowedBudgetMs = borrowedBudgetMs == null ? 0L : Math.max(borrowedBudgetMs, 0L);
        extendedTierBudgetMs = baseTierBudgetMs == null ? null : baseTierBudgetMs + borrowedBudgetMs;
        adaptiveBudgetActive = borrowedBudgetMs != null && borrowedBudgetMs > 0;
    }

    public synchronized void applyBorrowedBudget(long borrowedMillis) {
        borrowedBudgetMs = Math.max(borrowedMillis, 0L);
        adaptiveBudgetActive = borrowedBudgetMs > 0;
        extendedTierBudgetMs = baseTierBudgetMs == null ? null : baseTierBudgetMs + borrowedBudgetMs;
    }
}
