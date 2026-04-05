package com.scanner.app.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Scan;
import com.scanner.app.domain.User;
import com.scanner.app.repository.ScanRepository;
import com.scanner.app.repository.UserRepository;
import com.scanner.app.service.NotificationService;
import com.scanner.app.websocket.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TierScanEngine {

    private static final Logger log = LoggerFactory.getLogger(TierScanEngine.class);
    private static final Duration STEP_HEARTBEAT_INTERVAL = Duration.ofSeconds(5);

    private final ScanRepository scanRepository;
    private final TierPlanRegistry tierPlanRegistry;
    private final List<ToolStepExecutor> executors;
    private final ToolExecutionService toolExecutionService;
    private final LightCrawlerService lightCrawlerService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;
    private final NormalizedReportService normalizedReportService;
    private final TierRuntimeAvailabilityService tierRuntimeAvailabilityService;
    private final ZapDaemonManager zapDaemonManager;
    private final ObjectMapper executionContextMapper;

    public TierScanEngine(
            ScanRepository scanRepository,
            TierPlanRegistry tierPlanRegistry,
            List<ToolStepExecutor> executors,
            ToolExecutionService toolExecutionService,
            LightCrawlerService lightCrawlerService,
            NotificationService notificationService,
            UserRepository userRepository,
            EventPublisher eventPublisher,
            NormalizedReportService normalizedReportService,
            TierRuntimeAvailabilityService tierRuntimeAvailabilityService,
            ZapDaemonManager zapDaemonManager,
            ObjectMapper objectMapper
    ) {
        this.scanRepository = scanRepository;
        this.tierPlanRegistry = tierPlanRegistry;
        this.executors = executors;
        this.toolExecutionService = toolExecutionService;
        this.lightCrawlerService = lightCrawlerService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.normalizedReportService = normalizedReportService;
        this.tierRuntimeAvailabilityService = tierRuntimeAvailabilityService;
        this.zapDaemonManager = zapDaemonManager;
        this.executionContextMapper = objectMapper.copy().findAndRegisterModules();
    }

    public void runScan(Long scanId) {
        Scan scan = scanRepository.findWithContextById(scanId).orElse(null);
        if (scan == null || isCancelled(scan)) {
            return;
        }

        User notificationUser = resolveNotificationUser(scan);
        ScanTier tier = ScanTier.fromTargetValue(scan.getTier() != null ? scan.getTier() : scan.getProfileType());
        boolean timeoutsEnabled = scan.getTimeoutsEnabled() == null ? Boolean.TRUE : scan.getTimeoutsEnabled();
        TierPlan plan = tierPlanRegistry.planFor(tier, timeoutsEnabled);
        ScanExecutionContext context = loadExecutionContext(scan);
        context.setNormalizedTargetUrl(toolExecutionService.normalizeTargetUrl(scan.getTarget().getBaseUrl()));
        context.initializeBudget(plan.hardTimeout());

        boolean resumed = "PAUSED".equalsIgnoreCase(scan.getStatus()) || scan.getPausedAt() != null;
        int resumeStageOrder = scan.getResumeStageOrder() == null ? 1 : scan.getResumeStageOrder();
        int processedSteps = stepsBefore(plan, resumeStageOrder);

        try {
            tierRuntimeAvailabilityService.assertTierAvailable(tier);
        } catch (Exception exception) {
            log.warn("Scan {} blocked before execution due to tier runtime preflight failure", scanId, exception);
            finishFailed(scanId, context, notificationUser, TierRuntimeAvailabilityService.USER_SAFE_TIER_UNAVAILABLE_MESSAGE);
            return;
        }

        scan.setStatus("RUNNING");
        scan.setTier(tier.toPersistedValue());
        scan.setTimeoutsEnabled(timeoutsEnabled);
        scan.setPauseRequested(Boolean.FALSE);
        scan.setPausedAt(null);
        if (scan.getStartedAt() == null) {
            scan.setStartedAt(LocalDateTime.now());
        }
        saveContext(scan, context);
        scan.setUpdatedAt(LocalDateTime.now());
        saveScan(scan);
        eventPublisher.publishScanEvent(scanId, resumed ? "SCAN_RESUMED" : "SCAN_STARTED", resumed ? "Scan resumed." : "Scan started.");
        publishScanStatus(scan, context, processedSteps, plan.totalStepCount(), resumed ? "Scan resumed." : "Scan started.");
        publishScanProgress(scan, context, processedSteps, plan.totalStepCount(), resumeStageOrder, null, LocalDateTime.now());

        List<PlanStage> orderedStages = new ArrayList<>(plan.stages());
        orderedStages.sort(Comparator.comparingInt(PlanStage::order));

        try {
            for (PlanStage stage : orderedStages) {
                if (stage.order() < resumeStageOrder) {
                    continue;
                }

                scan = refreshScan(scanId, scan);
                if (checkForStopSignals(scan, stage.order())) {
                    return;
                }
                if (!ensureTierBudget(scan, plan, context, stage.order())) {
                    eventPublisher.publishScanEvent(scanId, "LOG", "[scan] The remaining scan window was used to finish earlier work.", stage.order());
                    break;
                }

                scan.setCurrentStageOrder(stage.order());
                scan.setResumeStageOrder(stage.order());
                scan.setUpdatedAt(LocalDateTime.now());
                saveScan(scan);
                eventPublisher.publishScanEvent(scanId, "STAGE_STARTED", stage.label() + " started.", stage.order());

                boolean stageHadFailure = false;
                List<PlanStep> orderedSteps = new ArrayList<>(stage.steps());
                orderedSteps.sort(Comparator.comparingInt(PlanStep::order));

                for (PlanStep step : orderedSteps) {
                    LocalDateTime stepStartedAt = LocalDateTime.now();
                    scan = refreshScan(scanId, scan);
                    if (checkForStopSignals(scan, stage.order())) {
                        return;
                    }
                    publishScanProgress(scan, context, processedSteps, plan.totalStepCount(), stage.order(), step, stepStartedAt);

                    if (shouldSkip(step, context)) {
                        context.getSkippedSteps().add(step.key());
                        saveContext(scan, context);
                        processedSteps += 1;
                        updateProgress(scan, processedSteps, plan.totalStepCount(), stage.order(), step, stepStartedAt);
                        eventPublisher.publishScanEvent(scanId, "LOG", "[scan] " + step.label() + " was skipped.");
                        continue;
                    }

                    context.beginStep(step.label());
                    saveContext(scan, context);
                    scan.setUpdatedAt(LocalDateTime.now());
                    saveScan(scan);
                    publishScanProgress(scan, context, processedSteps, plan.totalStepCount(), stage.order(), step, stepStartedAt);
                    StepExecutionResult result = executeStep(scan, plan, stage, step, context, processedSteps, plan.totalStepCount(), stepStartedAt);
                    context.merge(result);
                    classifyContext(context);
                    saveContext(scan, context);

                    if (result.skipped()) {
                        context.getSkippedSteps().add(step.key());
                    } else if (!result.success()) {
                        stageHadFailure = true;
                        if (result.fatal()) {
                            throw new IllegalStateException(result.message());
                        }
                    }

                    if (result.message() != null && !result.message().isBlank()) {
                        eventPublisher.publishScanEvent(scanId, "LOG", "[scan] " + result.message(), stage.order());
                    }

                    processedSteps += 1;
                    updateProgress(scan, processedSteps, plan.totalStepCount(), stage.order(), step, stepStartedAt);

                    scan = refreshScan(scanId, scan);
                    if (checkForStopSignals(scan, stage.order())) {
                        return;
                    }
                }

                context.addCompletedStage(stage.order());
                saveContext(scan, context);
                eventPublisher.publishScanEvent(
                        scanId,
                        stageHadFailure ? "STAGE_FAILED" : "STAGE_COMPLETED",
                        stageHadFailure ? stage.label() + " completed with recoverable issues." : stage.label() + " completed.",
                        stage.order()
                );
            }

            finishCompleted(scanId, context, notificationUser);
        } catch (Throwable throwable) {
            log.error("Scan {} failed during tier execution", scanId, throwable);
            finishFailed(scanId, context, notificationUser, failureMessage(throwable));
        } finally {
            try {
                zapDaemonManager.shutdown(scanId, context);
                Scan latestScan = refreshScan(scanId, null);
                if (latestScan != null) {
                    saveContext(latestScan, context);
                    latestScan.setUpdatedAt(LocalDateTime.now());
                    saveScan(latestScan);
                }
            } catch (Exception cleanupException) {
                log.debug("Unable to finalize scan cleanup for {}", scanId, cleanupException);
            }
        }
    }

    private StepExecutionResult executeStep(
            Scan scan,
            TierPlan plan,
            PlanStage stage,
            PlanStep step,
            ScanExecutionContext context,
            int processedSteps,
            int totalSteps,
            LocalDateTime stepStartedAt
    ) throws Exception {
        if ("normalize-url".equals(step.key())) {
            context.setNormalizedTargetUrl(toolExecutionService.normalizeTargetUrl(scan.getTarget().getBaseUrl()));
            if (context.getNormalizedTargetUrl() == null || context.getNormalizedTargetUrl().isBlank()) {
                return StepExecutionResult.fatalFailure("Target URL is invalid.");
            }
            return StepExecutionResult.success("Target URL normalized.");
        }
        if ("light-crawler".equals(step.key())) {
            return lightCrawlerService.crawl(
                    context.getNormalizedTargetUrl(),
                    step.intSetting("depth", 2),
                    step.intSetting("maxUrls", 30)
            );
        }
        if ("classify-assets".equals(step.key())) {
            classifyContext(context);
            return StepExecutionResult.success("Discovered assets classified.");
        }
        if ("expand-scope".equals(step.key())) {
            filterLowValueScope(context);
            classifyContext(context);
            return StepExecutionResult.success("Discovered scope expanded.");
        }
        if ("finalize-report".equals(step.key())) {
            normalizedReportService.persistReport(scan.getId());
            return StepExecutionResult.success("Normalized report generated.");
        }

        ToolStepExecutor executor = executors.stream()
                .filter(candidate -> candidate.supports(step))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No executor registered for step: " + step.key()));

        StepExecutionResult result = executeMonitoredStep(scan, plan, stage, step, context, executor, processedSteps, totalSteps, stepStartedAt);
        if (!result.success() && shouldRetryWholeStep(step, result)) {
            eventPublisher.publishScanEvent(scan.getId(), "LOG", "[scan] Retrying " + step.label() + " after a recoverable issue.", stage.order());
            context.beginStep(step.label());
            return executeMonitoredStep(scan, plan, stage, step, context, executor, processedSteps, totalSteps, LocalDateTime.now());
        }
        return result;
    }

    private StepExecutionResult executeMonitoredStep(
            Scan scan,
            TierPlan plan,
            PlanStage stage,
            PlanStep step,
            ScanExecutionContext context,
            ToolStepExecutor executor,
            int processedSteps,
            int totalSteps,
            LocalDateTime stepStartedAt
    ) throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<StepExecutionResult> forcedResult = new AtomicReference<>();
        AtomicReference<Future<StepExecutionResult>> stepFutureRef = new AtomicReference<>();
        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "scanner-step-heartbeat-" + scan.getId() + "-" + step.key());
            thread.setDaemon(true);
            return thread;
        });
        ExecutorService stepExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "scanner-step-runner-" + scan.getId() + "-" + step.key());
            thread.setDaemon(true);
            return thread;
        });

        try {
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                if (completed.get()) {
                    return;
                }

                Scan latestScan = refreshScan(scan.getId(), scan);
                if (latestScan == null) {
                    forcedResult.compareAndSet(null, StepExecutionResult.skipped("Scan state changed before the stage could finish."));
                    return;
                }

                if (isCancelled(latestScan) || isPauseRequested(latestScan)) {
                    toolExecutionService.stopActiveProcesses(scan.getId());
                    forcedResult.compareAndSet(null, StepExecutionResult.skipped("Scan state changed before the stage could finish."));
                    cancelStepFuture(stepFutureRef.get());
                    return;
                }

                if (!ensureTierBudget(latestScan, plan, context, stage.order())) {
                    toolExecutionService.stopActiveProcesses(scan.getId());
                    forcedResult.compareAndSet(null, buildBudgetExceededResult(step));
                    cancelStepFuture(stepFutureRef.get());
                    return;
                }

                if (shouldAbortStalledStep(plan, step, context)) {
                    toolExecutionService.stopActiveProcesses(scan.getId());
                    forcedResult.compareAndSet(null, buildStalledStepResult(step));
                    cancelStepFuture(stepFutureRef.get());
                    return;
                }

                publishScanProgress(latestScan, context, processedSteps, totalSteps, stage.order(), step, stepStartedAt);
            }, STEP_HEARTBEAT_INTERVAL.toMillis(), STEP_HEARTBEAT_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);

            try {
                Future<StepExecutionResult> stepFuture = stepExecutor.submit(() -> executor.execute(scan, step, context, eventPublisher));
                stepFutureRef.set(stepFuture);

                while (true) {
                    StepExecutionResult override = forcedResult.get();
                    if (override != null) {
                        cancelStepFuture(stepFuture);
                        return override;
                    }

                    try {
                        StepExecutionResult result = stepFuture.get(250, TimeUnit.MILLISECONDS);
                        override = forcedResult.get();
                        return override != null ? override : result;
                    } catch (TimeoutException ignored) {
                        // Keep polling while the heartbeat monitors the step.
                    } catch (CancellationException cancellationException) {
                        override = forcedResult.get();
                        if (override != null) {
                            return override;
                        }
                        return mapExecutorFailure(scan, step, cancellationException);
                    } catch (ExecutionException executionException) {
                        Throwable cause = executionException.getCause() == null ? executionException : executionException.getCause();
                        override = forcedResult.get();
                        if (override != null) {
                            return override;
                        }
                        return mapExecutorFailure(scan, step, cause);
                    }
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                StepExecutionResult override = forcedResult.get();
                if (override != null) {
                    return override;
                }
                return mapExecutorFailure(scan, step, interruptedException);
            } finally {
                completed.set(true);
            }
        } finally {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor.awaitTermination(1, TimeUnit.SECONDS);
            stepExecutor.shutdownNow();
            stepExecutor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private void cancelStepFuture(Future<StepExecutionResult> stepFuture) {
        if (stepFuture == null || stepFuture.isDone()) {
            return;
        }

        try {
            stepFuture.cancel(true);
        } catch (Exception ignored) {
            // Best-effort interruption only.
        }
    }

    private boolean shouldRetryWholeStep(PlanStep step, StepExecutionResult result) {
        return step.retryable()
                && !result.fatal()
                && !result.skipped()
                && !result.timedOut()
                && !"nuclei-discovered".equals(step.key());
    }

    private StepExecutionResult mapExecutorFailure(Scan scan, PlanStep step, Throwable throwable) {
        log.warn("Step {} failed for scan {}", step.key(), scan.getId(), throwable);
        Scan latestScan = refreshScan(scan.getId(), scan);
        if (isCancelled(latestScan) || isPauseRequested(latestScan)) {
            return StepExecutionResult.skipped("Scan state changed before the stage could finish.");
        }
        if ("httpx".equals(step.key())) {
            return StepExecutionResult.fatalFailure("Baseline reachability check failed.");
        }
        return StepExecutionResult.nonFatalFailure(step.label() + " could not be completed in this environment.", false);
    }

    private StepExecutionResult buildBudgetExceededResult(PlanStep step) {
        if ("httpx".equals(step.key())) {
            return StepExecutionResult.fatalFailure("Baseline reachability check failed.");
        }
        return StepExecutionResult.nonFatalFailure(step.label() + " timed out.", true);
    }

    private boolean shouldAbortStalledStep(TierPlan plan, PlanStep step, ScanExecutionContext context) {
        if (step.timeout() != null) {
            return false;
        }

        return !context.hasRecentForwardProgress(stallWindowFor(plan.tier(), step));
    }

    private StepExecutionResult buildStalledStepResult(PlanStep step) {
        if ("httpx".equals(step.key())) {
            return StepExecutionResult.fatalFailure("Baseline reachability check stalled in this environment.");
        }
        return StepExecutionResult.nonFatalFailure(step.label() + " stalled and was stopped to keep the scan moving.", true);
    }

    private void finishCompleted(Long scanId, ScanExecutionContext context, User notificationUser) {
        Scan scan = refreshScan(scanId, null);
        if (scan == null || isCancelled(scan)) {
            return;
        }
        if (isPauseRequested(scan)) {
            pauseScan(scan, scan.getCurrentStageOrder() == null ? 1 : scan.getCurrentStageOrder());
            return;
        }

        NormalizedScanReport report = persistNormalizedReportSafely(scanId, scan);
        scan.setStatus("COMPLETED");
        scan.setProgress(100);
        scan.setCompletedAt(LocalDateTime.now());
        scan.setPauseRequested(Boolean.FALSE);
        scan.setCurrentStageOrder(null);
        scan.setResumeStageOrder(null);
        scan.setPausedAt(null);
        saveContext(scan, context);
        scan.setUpdatedAt(LocalDateTime.now());
        saveScan(scan);
        eventPublisher.publishScanEvent(scanId, "SCAN_COMPLETED", "Scan completed.");
        publishScanStatus(scan, context, null, null, "Scan completed.");

        if (notificationUser != null) {
            String targetName = scan.getTarget() != null && scan.getTarget().getName() != null ? scan.getTarget().getName() : "target";
            int findingCount = report.getFindings().size();
            notificationService.createNotification(
                    notificationUser,
                    "Scan Completed",
                    targetName + " completed with " + findingCount + " findings.",
                    "SUCCESS",
                    scanId,
                    scan.getTarget() != null ? scan.getTarget().getId() : null,
                    findingCount
            );
        }
    }

    private void finishFailed(Long scanId, ScanExecutionContext context, User notificationUser, String message) {
        Scan scan = refreshScan(scanId, null);
        if (scan == null || isCancelled(scan)) {
            return;
        }
        if (isPauseRequested(scan)) {
            pauseScan(scan, scan.getCurrentStageOrder() == null ? 1 : scan.getCurrentStageOrder());
            return;
        }

        persistNormalizedReportSafely(scanId, scan);
        scan.setStatus("FAILED");
        scan.setCompletedAt(LocalDateTime.now());
        scan.setPauseRequested(Boolean.FALSE);
        scan.setPausedAt(null);
        saveContext(scan, context);
        scan.setUpdatedAt(LocalDateTime.now());
        saveScan(scan);
        eventPublisher.publishScanEvent(scanId, "SCAN_FAILED", "Execution failed: " + message);
        publishScanStatus(scan, context, null, null, "Execution failed.");

        if (notificationUser != null) {
            String targetName = scan.getTarget() != null && scan.getTarget().getName() != null && !scan.getTarget().getName().isBlank()
                    ? scan.getTarget().getName().trim()
                    : scan.getTarget() != null && scan.getTarget().getDomain() != null && !scan.getTarget().getDomain().isBlank()
                        ? scan.getTarget().getDomain()
                        : "Target";
            notificationService.createNotification(
                    notificationUser,
                    "Scan Failed",
                    targetName + " failed.",
                    "ERROR",
                    scanId,
                    scan.getTarget() != null ? scan.getTarget().getId() : null,
                    null
            );
        }
    }

    private void updateProgress(Scan scan, int processedSteps, int totalSteps, Integer stageOrder, PlanStep step, LocalDateTime stepStartedAt) {
        scan.setProgress(Math.min(100, (int) Math.round((processedSteps * 100.0) / Math.max(totalSteps, 1))));
        scan.setUpdatedAt(LocalDateTime.now());
        saveScan(scan);
        publishScanProgress(scan, loadExecutionContext(scan), processedSteps, totalSteps, stageOrder, step, stepStartedAt);
    }

    private boolean shouldSkip(PlanStep step, ScanExecutionContext context) {
        return switch (step.key()) {
            case "nuclei-discovered" -> context.getDiscoveredUrls().isEmpty();
            case "zap-active" -> !context.hasDiscoveredScope();
            case "dalfox", "sqlmap" -> !context.hasInjectableInputs();
            default -> false;
        };
    }

    private void classifyContext(ScanExecutionContext context) {
        Set<String> filteredUrls = context.getDiscoveredUrls().stream()
                .filter(url -> url != null && !url.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        context.setDiscoveredUrls(filteredUrls);

        for (String url : filteredUrls) {
            int queryStart = url.indexOf('?');
            if (queryStart >= 0 && queryStart < url.length() - 1) {
                for (String segment : url.substring(queryStart + 1).split("&")) {
                    int separator = segment.indexOf('=');
                    context.getQueryParameters().add(separator >= 0 ? segment.substring(0, separator) : segment);
                }
            }
            String normalized = url.toLowerCase(Locale.ROOT);
            if (normalized.endsWith(".json") || normalized.contains("/api/")) {
                context.getJsonEndpoints().add(url);
            }
            if (normalized.contains("login") || normalized.contains("register") || normalized.contains("submit") || normalized.contains("form")) {
                context.getForms().add(url);
            }
        }
    }

    private void filterLowValueScope(ScanExecutionContext context) {
        Set<String> filtered = context.getDiscoveredUrls().stream()
                .filter(url -> {
                    String normalized = url.toLowerCase(Locale.ROOT);
                    return !(normalized.endsWith(".png")
                            || normalized.endsWith(".jpg")
                            || normalized.endsWith(".jpeg")
                            || normalized.endsWith(".gif")
                            || normalized.endsWith(".svg")
                            || normalized.endsWith(".css")
                            || normalized.endsWith(".woff")
                            || normalized.endsWith(".woff2")
                            || normalized.endsWith(".ico"));
                })
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        context.setDiscoveredUrls(filtered);
    }

    private int stepsBefore(TierPlan plan, int resumeStageOrder) {
        return plan.stages().stream()
                .filter(stage -> stage.order() < resumeStageOrder)
                .mapToInt(stage -> stage.steps().size())
                .sum();
    }

    private boolean checkForStopSignals(Scan scan, int stageOrder) {
        if (isCancelled(scan)) {
            return true;
        }
        if (isPauseRequested(scan)) {
            pauseScan(scan, stageOrder);
            return true;
        }
        return false;
    }

    private boolean isPauseRequested(Scan scan) {
        return scan != null && (Boolean.TRUE.equals(scan.getPauseRequested()) || "PAUSING".equalsIgnoreCase(scan.getStatus()));
    }

    private boolean isCancelled(Scan scan) {
        return scan != null && "CANCELLED".equalsIgnoreCase(scan.getStatus());
    }

    private void pauseScan(Scan scan, int stageOrder) {
        scan.setStatus("PAUSED");
        scan.setPauseRequested(Boolean.FALSE);
        scan.setPausedAt(LocalDateTime.now());
        scan.setResumeStageOrder(stageOrder <= 0 ? 1 : stageOrder);
        scan.setCurrentStageOrder(stageOrder <= 0 ? null : stageOrder);
        scan.setUpdatedAt(LocalDateTime.now());
        saveScan(scan, false);
        eventPublisher.publishScanEvent(scan.getId(), "SCAN_PAUSED", "Scan paused. Resume will restart from the current stage.", stageOrder <= 0 ? null : stageOrder);
        publishScanStatus(scan, loadExecutionContext(scan), null, null, "Scan paused.");
    }

    private void publishScanProgress(Scan scan, ScanExecutionContext context, int processedSteps, int totalSteps, Integer stageOrder, PlanStep step, LocalDateTime stepStartedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", scan.getStatus());
        payload.put("progress", scan.getProgress() == null ? 0 : scan.getProgress());
        payload.put("currentStageOrder", stageOrder);
        payload.put("processedSteps", processedSteps);
        payload.put("totalSteps", totalSteps);
        payload.put("stepStartedAt", stepStartedAt);
        payload.put("stepExpectedDurationMs", step != null && step.timeout() != null ? step.timeout().toMillis() : null);
        payload.put("stepLabel", step == null ? null : step.label());
        payload.put("adaptiveBudgetActive", Boolean.TRUE.equals(context.getAdaptiveBudgetActive()));
        payload.put("borrowedBudgetMs", context.getBorrowedBudgetMs() == null ? 0L : context.getBorrowedBudgetMs());
        payload.put("baseTierBudgetMs", context.getBaseTierBudgetMs());
        payload.put("extendedTierBudgetMs", context.getExtendedTierBudgetMs());
        payload.put("stageProgressPercent", context.getStageProgressPercent());
        payload.put("batchCompleted", context.getBatchCompleted());
        payload.put("batchTotal", context.getBatchTotal());
        eventPublisher.publishScanProgress(scan.getId(), payload);
    }

    private void publishScanStatus(Scan scan, ScanExecutionContext context, Integer processedSteps, Integer totalSteps, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", scan.getStatus());
        payload.put("progress", scan.getProgress() == null ? 0 : scan.getProgress());
        payload.put("currentStageOrder", scan.getCurrentStageOrder());
        payload.put("processedSteps", processedSteps);
        payload.put("totalSteps", totalSteps);
        payload.put("message", message);
        payload.put("adaptiveBudgetActive", Boolean.TRUE.equals(context.getAdaptiveBudgetActive()));
        payload.put("borrowedBudgetMs", context.getBorrowedBudgetMs() == null ? 0L : context.getBorrowedBudgetMs());
        payload.put("baseTierBudgetMs", context.getBaseTierBudgetMs());
        payload.put("extendedTierBudgetMs", context.getExtendedTierBudgetMs());
        eventPublisher.publishScanStatus(scan.getId(), payload);
    }

    private ScanExecutionContext loadExecutionContext(Scan scan) {
        if (scan.getExecutionContextJson() == null || scan.getExecutionContextJson().isBlank()) {
            return new ScanExecutionContext();
        }
        try {
            return executionContextMapper.readValue(scan.getExecutionContextJson(), ScanExecutionContext.class);
        } catch (Exception ignored) {
            return new ScanExecutionContext();
        }
    }

    private void saveContext(Scan scan, ScanExecutionContext context) {
        try {
            scan.setExecutionContextJson(executionContextMapper.writeValueAsString(context));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize execution context.", exception);
        }
    }

    private Scan refreshScan(Long scanId, Scan fallback) {
        return scanRepository.findWithContextById(scanId).orElse(fallback);
    }

    private void saveScan(Scan scan) {
        saveScan(scan, true);
    }

    private void saveScan(Scan scan, boolean preserveControlState) {
        if (scan == null) {
            return;
        }
        if (preserveControlState) {
            preserveExternalControlState(scan);
        }
        scanRepository.save(scan);
    }

    private void preserveExternalControlState(Scan scan) {
        if (scan == null || scan.getId() == null) {
            return;
        }

        scanRepository.findById(scan.getId()).ifPresent(latest -> {
            if (isCancelled(latest)) {
                scan.setStatus(latest.getStatus());
                scan.setCompletedAt(latest.getCompletedAt());
            }

            boolean localAlreadyPaused = "PAUSED".equalsIgnoreCase(scan.getStatus());

            if (Boolean.TRUE.equals(latest.getPauseRequested()) && !localAlreadyPaused) {
                scan.setPauseRequested(Boolean.TRUE);
            }

            if ("PAUSING".equalsIgnoreCase(latest.getStatus()) && !localAlreadyPaused) {
                scan.setStatus(latest.getStatus());
                scan.setPauseRequested(latest.getPauseRequested());
                scan.setPausedAt(latest.getPausedAt());
                scan.setResumeStageOrder(latest.getResumeStageOrder());
                scan.setCurrentStageOrder(latest.getCurrentStageOrder());
            }

            if ("PAUSED".equalsIgnoreCase(latest.getStatus())) {
                scan.setStatus(latest.getStatus());
                scan.setPauseRequested(latest.getPauseRequested());
                scan.setPausedAt(latest.getPausedAt());
                scan.setResumeStageOrder(latest.getResumeStageOrder());
                scan.setCurrentStageOrder(latest.getCurrentStageOrder());
            }
        });
    }

    private User resolveNotificationUser(Scan scan) {
        if (scan.getTarget() != null && scan.getTarget().getUser() != null && scan.getTarget().getUser().getId() != null) {
            return userRepository.findById(scan.getTarget().getUser().getId()).orElse(scan.getTarget().getUser());
        }
        if (scan.getUser() != null && scan.getUser().getId() != null) {
            return userRepository.findById(scan.getUser().getId()).orElse(scan.getUser());
        }
        return null;
    }

    private NormalizedScanReport persistNormalizedReportSafely(Long scanId, Scan scan) {
        try {
            return normalizedReportService.persistReport(scanId);
        } catch (Exception exception) {
            log.warn("Unable to persist normalized report for scan {}", scanId, exception);
            try {
                return normalizedReportService.buildReport(scan);
            } catch (Exception ignored) {
                return new NormalizedScanReport();
            }
        }
    }

    private String failureMessage(Throwable throwable) {
        if (throwable == null) {
            return "The scan could not be completed.";
        }

        String message = throwable.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return throwable.getClass().getSimpleName();
    }

    private boolean ensureTierBudget(Scan scan, TierPlan plan, ScanExecutionContext context, Integer stageOrder) {
        if (scan == null || scan.getStartedAt() == null || plan.hardTimeout() == null) {
            return true;
        }

        context.initializeBudget(plan.hardTimeout());
        long baseBudgetMs = context.getBaseTierBudgetMs() == null ? 0L : context.getBaseTierBudgetMs();
        long maxExtraBudgetMs = Math.round(baseBudgetMs * 0.25d);
        long borrowedBudgetMs = context.getBorrowedBudgetMs() == null ? 0L : context.getBorrowedBudgetMs();
        long elapsedMs = Duration.between(scan.getStartedAt(), LocalDateTime.now()).toMillis();

        while (elapsedMs > baseBudgetMs + borrowedBudgetMs) {
            if (!context.hasRecentForwardProgress(progressWindowFor(plan.tier()))) {
                return false;
            }

            long remainingBorrowableMs = maxExtraBudgetMs - borrowedBudgetMs;
            if (remainingBorrowableMs <= 0) {
                return false;
            }

            long sliceMs = Math.min(adaptiveSliceFor(plan.tier()).toMillis(), remainingBorrowableMs);
            long nextBorrowedBudgetMs = borrowedBudgetMs + sliceMs;
            boolean adaptiveJustStarted = borrowedBudgetMs == 0;
            context.applyBorrowedBudget(nextBorrowedBudgetMs);

            if (adaptiveJustStarted) {
                eventPublisher.publishScanEvent(scan.getId(), "LOG", "Extending scan window to finish current stage", stageOrder);
            }

            saveContext(scan, context);
            scan.setUpdatedAt(LocalDateTime.now());
            saveScan(scan);
            borrowedBudgetMs = nextBorrowedBudgetMs;
        }

        return true;
    }

    private Duration adaptiveSliceFor(ScanTier tier) {
        return switch (tier) {
            case FAST -> Duration.ofSeconds(15);
            case MEDIUM, DEEP -> Duration.ofSeconds(30);
        };
    }

    private Duration progressWindowFor(ScanTier tier) {
        return switch (tier) {
            case FAST -> Duration.ofSeconds(20);
            case MEDIUM, DEEP -> Duration.ofSeconds(30);
        };
    }

    private Duration stallWindowFor(ScanTier tier, PlanStep step) {
        int fallbackSeconds = switch (tier) {
            case FAST -> 90;
            case MEDIUM -> 180;
            case DEEP -> 300;
        };
        return Duration.ofSeconds(Math.max(1, step.intSetting("stallWindowSeconds", fallbackSeconds)));
    }
}
