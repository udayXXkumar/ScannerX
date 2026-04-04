package com.scanner.app.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.app.domain.Scan;
import com.scanner.app.service.FindingService;
import com.scanner.app.websocket.EventPublisher;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class NucleiExecutor extends AbstractFindingExecutor {

    private static final int DEFAULT_BATCH_SIZE = 6;
    private static final int DEFAULT_DISCOVERED_TARGET_LIMIT = 24;

    private final ObjectMapper objectMapper;

    public NucleiExecutor(FindingService findingService, ToolExecutionService toolExecutionService, ObjectMapper objectMapper) {
        super(findingService, toolExecutionService);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getExecutorName() {
        return "nuclei";
    }

    @Override
    public boolean supports(PlanStep step) {
        return step.key().startsWith("nuclei");
    }

    @Override
    public StepExecutionResult execute(Scan scan, PlanStep step, ScanExecutionContext context, EventPublisher eventPublisher) throws Exception {
        List<String> targets = resolveTargets(step, context);
        if (targets.isEmpty()) {
            return StepExecutionResult.skipped(step.label() + " were skipped because no scan scope was discovered.");
        }

        boolean discoveredScope = "discovered".equalsIgnoreCase(step.setting("targetSource", "root"));
        if (!discoveredScope) {
            BatchExecutionOutcome outcome = executeBatch(scan, step, context, eventPublisher, targets, step.timeout());
            if (outcome.success()) {
                return StepExecutionResult.success(step.label() + " completed.");
            }
            return StepExecutionResult.nonFatalFailure(
                    outcome.timedOut() ? step.label() + " timed out." : step.label() + " finished with recoverable issues.",
                    outcome.timedOut()
            );
        }

        List<String> prioritizedTargets = prioritizeTargets(targets, context);
        int targetLimit = Math.max(1, step.intSetting("maxTargets", DEFAULT_DISCOVERED_TARGET_LIMIT));
        if (prioritizedTargets.size() > targetLimit) {
            prioritizedTargets = new ArrayList<>(prioritizedTargets.subList(0, targetLimit));
        }

        int batchSize = Math.max(1, step.intSetting("batchSize", DEFAULT_BATCH_SIZE));
        int totalBatches = (int) Math.ceil(prioritizedTargets.size() / (double) batchSize);
        context.setBatchCompleted(0);
        context.setBatchTotal(totalBatches);
        context.setStageProgressPercent(totalBatches == 0 ? null : 0);

        long deadlineNanos = step.timeout() == null ? Long.MAX_VALUE : System.nanoTime() + step.timeout().toNanos();
        boolean hadRecoverableIssues = false;
        boolean timedOut = false;
        int completedBatches = 0;

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int fromIndex = batchIndex * batchSize;
            int toIndex = Math.min(fromIndex + batchSize, prioritizedTargets.size());
            List<String> batchTargets = prioritizedTargets.subList(fromIndex, toIndex);

            BatchExecutionOutcome outcome = executeBatchWithRetry(scan, step, context, eventPublisher, batchTargets, deadlineNanos, stageRetryMessage(step.label()));
            completedBatches += 1;
            context.updateBatchProgress(completedBatches, totalBatches);

            if (!outcome.success()) {
                hadRecoverableIssues = true;
                timedOut = timedOut || outcome.timedOut();
            }

            if (deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos && batchIndex + 1 < totalBatches) {
                hadRecoverableIssues = true;
                timedOut = true;
                break;
            }
        }

        if (hadRecoverableIssues) {
            return StepExecutionResult.nonFatalFailure(
                    timedOut ? step.label() + " timed out." : step.label() + " finished with recoverable issues.",
                    timedOut
            );
        }

        return StepExecutionResult.success(step.label() + " completed.");
    }

    private BatchExecutionOutcome executeBatchWithRetry(
            Scan scan,
            PlanStep step,
            ScanExecutionContext context,
            EventPublisher eventPublisher,
            List<String> batchTargets,
            long deadlineNanos,
            String retryMessage
    ) throws Exception {
        Duration firstTimeout = resolveBatchTimeout(step, deadlineNanos);
        BatchExecutionOutcome firstAttempt = executeBatch(scan, step, context, eventPublisher, batchTargets, firstTimeout);
        if (firstAttempt.success()) {
            return firstAttempt;
        }

        if (!step.retryable() || !hasRemainingBudget(deadlineNanos, Duration.ofSeconds(10))) {
            return firstAttempt;
        }

        eventPublisher.publishScanEvent(scan.getId(), "LOG", "[scan] " + retryMessage, scan.getCurrentStageOrder());
        Duration retryTimeout = resolveBatchTimeout(step, deadlineNanos);
        if (retryTimeout != null && retryTimeout.isZero()) {
            return firstAttempt;
        }
        return executeBatch(scan, step, context, eventPublisher, batchTargets, retryTimeout);
    }

    private BatchExecutionOutcome executeBatch(
            Scan scan,
            PlanStep step,
            ScanExecutionContext context,
            EventPublisher eventPublisher,
            List<String> targets,
            Duration timeout
    ) throws Exception {
        Path targetFile = Files.createTempFile("scannerx-nuclei-", ".txt");
        Files.write(targetFile, targets);
        try {
            ToolExecutionService.StreamingProcessResult result = toolExecutionService.runStreamingProcess(
                    scan.getId(),
                    List.of("nuclei", "-l", targetFile.toString(), "-j", "-silent", "-rl", "10", "-timeout", "5"),
                    List.of("docker", "run", "--rm", "-v", targetFile.toAbsolutePath() + ":/targets.txt:ro", "projectdiscovery/nuclei:latest", "-l", "/targets.txt", "-j"),
                    timeout,
                    line -> handleOutput(scan, context, eventPublisher, line)
            );

            if (result.timedOut()) {
            return BatchExecutionOutcome.timedOutResult();
        }
        if (result.exitCode() != 0) {
            return BatchExecutionOutcome.failedResult();
        }
        return BatchExecutionOutcome.successResult();
        } finally {
            Files.deleteIfExists(targetFile);
        }
    }

    private void handleOutput(Scan scan, ScanExecutionContext context, EventPublisher eventPublisher, String line) {
        String cleanLine = toolExecutionService.stripAnsi(line).trim();
        if (!cleanLine.startsWith("{")) {
            return;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(cleanLine);
            String title = jsonNode.path("info").path("name").asText("").trim();
            String severity = jsonNode.path("info").path("severity").asText(null);
            String templateId = jsonNode.path("template-id").asText("security-check");
            String matched = jsonNode.path("matched-at").asText(context.getNormalizedTargetUrl());

            saveFinding(
                    scan,
                    eventPublisher,
                    context,
                    "Nuclei",
                    "Passive",
                    title.isBlank() ? "Security Result" : title,
                    severity,
                    matched,
                    buildDescription(jsonNode, templateId),
                    cleanLine
            );
        } catch (Exception ignored) {
            // Ignore malformed lines.
        }
    }

    private List<String> resolveTargets(PlanStep step, ScanExecutionContext context) {
        String targetSource = step.setting("targetSource", "root");
        return switch (targetSource) {
            case "discovered" -> context.getDiscoveredUrls().stream().toList();
            default -> List.of(context.getNormalizedTargetUrl());
        };
    }

    private List<String> prioritizeTargets(List<String> targets, ScanExecutionContext context) {
        return targets.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(Comparator
                        .comparingInt((String url) -> scoreTarget(url, context))
                        .reversed()
                        .thenComparingInt(String::length))
                .toList();
    }

    private int scoreTarget(String url, ScanExecutionContext context) {
        String normalized = url.toLowerCase(Locale.ROOT);
        int score = 0;

        if (context.getForms().contains(url)) {
            score += 90;
        }
        if (context.getJsonEndpoints().contains(url) || normalized.endsWith(".json") || normalized.contains("/api/")) {
            score += 85;
        }
        if (normalized.contains("?")) {
            score += 70;
        }
        if (containsInterestingPath(normalized, "admin", "dashboard", "panel", "manage", "login", "register", "auth")) {
            score += 60;
        }
        if (containsInterestingPath(normalized, "swagger", "graphql", "docs", "openapi", "report", "submit", "contact")) {
            score += 45;
        }
        if (isHtmlLikeRoute(normalized)) {
            score += 35;
        }
        if (isLowValueStaticAsset(normalized)) {
            score -= 90;
        }
        if (normalized.equals(String.valueOf(context.getNormalizedTargetUrl()).toLowerCase(Locale.ROOT))) {
            score += 10;
        }

        return score;
    }

    private boolean containsInterestingPath(String normalizedUrl, String... keywords) {
        for (String keyword : keywords) {
            if (normalizedUrl.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHtmlLikeRoute(String normalizedUrl) {
        return normalizedUrl.endsWith(".html")
                || normalizedUrl.endsWith("/")
                || !normalizedUrl.matches(".*\\.[a-z0-9]{2,6}(?:\\?.*)?$");
    }

    private boolean isLowValueStaticAsset(String normalizedUrl) {
        return normalizedUrl.endsWith(".png")
                || normalizedUrl.endsWith(".jpg")
                || normalizedUrl.endsWith(".jpeg")
                || normalizedUrl.endsWith(".gif")
                || normalizedUrl.endsWith(".svg")
                || normalizedUrl.endsWith(".css")
                || normalizedUrl.endsWith(".ico")
                || normalizedUrl.endsWith(".woff")
                || normalizedUrl.endsWith(".woff2")
                || normalizedUrl.endsWith(".map");
    }

    private Duration resolveBatchTimeout(PlanStep step, long deadlineNanos) {
        Duration configuredBatchTimeout = Duration.ofSeconds(Math.max(5, step.intSetting("batchTimeoutSeconds", 30)));
        if (deadlineNanos == Long.MAX_VALUE) {
            return configuredBatchTimeout;
        }

        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            return Duration.ZERO;
        }

        Duration remaining = Duration.ofNanos(remainingNanos);
        return remaining.compareTo(configuredBatchTimeout) < 0 ? remaining : configuredBatchTimeout;
    }

    private boolean hasRemainingBudget(long deadlineNanos, Duration minimumRemainingBudget) {
        if (deadlineNanos == Long.MAX_VALUE) {
            return true;
        }
        return deadlineNanos - System.nanoTime() > minimumRemainingBudget.toNanos();
    }

    private String buildDescription(JsonNode jsonNode, String templateId) {
        String description = jsonNode.path("info").path("description").asText("").trim();
        if (!description.isBlank()) {
            return description;
        }
        String name = jsonNode.path("info").path("name").asText("").trim();
        if (!name.isBlank()) {
            return name;
        }
        return "Matched security rule: " + templateId;
    }

    private String stageRetryMessage(String stepLabel) {
        return "Retry 1/1 started for " + stepLabel + ".";
    }

    private record BatchExecutionOutcome(boolean success, boolean timedOut) {
        public static BatchExecutionOutcome successResult() {
            return new BatchExecutionOutcome(true, false);
        }

        public static BatchExecutionOutcome failedResult() {
            return new BatchExecutionOutcome(false, false);
        }

        public static BatchExecutionOutcome timedOutResult() {
            return new BatchExecutionOutcome(false, true);
        }
    }
}
