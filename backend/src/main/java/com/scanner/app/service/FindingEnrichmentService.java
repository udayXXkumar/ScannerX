package com.scanner.app.service;

import com.scanner.app.domain.Finding;
import com.scanner.app.orchestrator.ScanTier;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.websocket.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
public class FindingEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(FindingEnrichmentService.class);

    private final FindingRepository findingRepository;
    private final AiInferenceClient huggingFaceInferenceClient;
    private final EventPublisher eventPublisher;
    private final Executor findingEnrichmentExecutor;
    private final boolean enrichmentEnabled;
    private final int maxInputChars;
    private final int maxRetries;
    private final long fastScanWaitTimeoutMs;
    private final long mediumScanWaitTimeoutMs;
    private final long deepScanWaitTimeoutMs;
    private final java.util.Set<Long> queuedFindingIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Long, AtomicInteger> activeJobsByScan = new ConcurrentHashMap<>();
    private final Object activeJobsMonitor = new Object();
    private final ConcurrentMap<Long, ScanEnrichmentProgress> scanProgress = new ConcurrentHashMap<>();

    public FindingEnrichmentService(
            FindingRepository findingRepository,
            AiInferenceClient huggingFaceInferenceClient,
            EventPublisher eventPublisher,
            @Qualifier("findingEnrichmentExecutor") Executor findingEnrichmentExecutor,
            @Value("${app.ai.finding-enrichment.enabled:true}") boolean enrichmentEnabled,
            @Value("${app.ai.finding-enrichment.max-input-chars:6000}") int maxInputChars,
            @Value("${app.ai.finding-enrichment.max-retries:1}") int maxRetries,
            @Value("${app.ai.finding-enrichment.fast-timeout-ms:120000}") long fastScanWaitTimeoutMs,
            @Value("${app.ai.finding-enrichment.medium-timeout-ms:300000}") long mediumScanWaitTimeoutMs,
            @Value("${app.ai.finding-enrichment.deep-timeout-ms:900000}") long deepScanWaitTimeoutMs
    ) {
        this.findingRepository = findingRepository;
        this.huggingFaceInferenceClient = huggingFaceInferenceClient;
        this.eventPublisher = eventPublisher;
        this.findingEnrichmentExecutor = findingEnrichmentExecutor;
        this.enrichmentEnabled = enrichmentEnabled;
        this.maxInputChars = Math.max(500, maxInputChars);
        this.maxRetries = Math.max(0, maxRetries);
        this.fastScanWaitTimeoutMs = Math.max(1000, fastScanWaitTimeoutMs);
        this.mediumScanWaitTimeoutMs = Math.max(1000, mediumScanWaitTimeoutMs);
        this.deepScanWaitTimeoutMs = Math.max(1000, deepScanWaitTimeoutMs);
    }

    /** Queue AI work as scanners persist results; API latency overlaps scanner execution. */
    public void enqueueFinding(Long findingId, Long scanId) {
        if (findingId == null || scanId == null || !isReadyToRun() || !queuedFindingIds.add(findingId)) {
            return;
        }

        incrementActiveJobs(scanId);
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    enrichFindingNow(findingId);
                } catch (Exception exception) {
                    logger.error("Unexpected AI enrichment error for finding {}", findingId, exception);
                } finally {
                    queuedFindingIds.remove(findingId);
                    markScanProgressDone(scanId, findingId);
                    decrementActiveJobs(scanId);
                }
            }, findingEnrichmentExecutor);
        } catch (RuntimeException exception) {
            queuedFindingIds.remove(findingId);
            markScanProgressDone(scanId, findingId);
            decrementActiveJobs(scanId);
            logger.warn("Unable to queue AI enrichment for finding {}", findingId, exception);
        }
    }

    private void incrementActiveJobs(Long scanId) {
        activeJobsByScan.compute(scanId, (ignored, activeJobs) -> {
            AtomicInteger counter = activeJobs == null ? new AtomicInteger() : activeJobs;
            counter.incrementAndGet();
            return counter;
        });
    }

    private void decrementActiveJobs(Long scanId) {
        activeJobsByScan.compute(scanId, (ignored, activeJobs) -> {
            if (activeJobs == null || activeJobs.decrementAndGet() <= 0) {
                return null;
            }
            return activeJobs;
        });
        synchronized (activeJobsMonitor) {
            activeJobsMonitor.notifyAll();
        }
    }

    /** Ensure any results not queued during execution are processed before finalizing the scan. */
    public void enrichScanFindings(Long scanId) {
        enrichScanFindings(scanId, ScanTier.MEDIUM, (completed, total) -> { });
    }

    public boolean isReadyToRun() {
        return enrichmentEnabled && huggingFaceInferenceClient.isConfigured();
    }

    public void enrichScanFindings(Long scanId, BiConsumer<Integer, Integer> progressListener) {
        enrichScanFindings(scanId, ScanTier.MEDIUM, progressListener);
    }

    public void enrichScanFindings(Long scanId, ScanTier tier, BiConsumer<Integer, Integer> progressListener) {
        if (scanId == null) {
            return;
        }
        if (!isReadyToRun()) {
            logger.warn("AI enrichment skipped for scan {} (enabled={}, provider credential configured={})",
                    scanId, enrichmentEnabled, huggingFaceInferenceClient.isConfigured());
            progressListener.accept(0, 0);
            return;
        }

        List<Finding> scanFindings = findingRepository.findByScanIdOrderByCreatedAtDesc(scanId);
        List<Long> eligibleIds = scanFindings.stream().filter(this::isEligible).map(Finding::getId).toList();
        ScanEnrichmentProgress tracker = new ScanEnrichmentProgress(eligibleIds.size(), progressListener);
        scanProgress.put(scanId, tracker);
        tracker.publish();

        // Re-read terminal rows after registering the tracker to close the race with jobs
        // that finish just as the scan moves from scanner work to AI work.
        findingRepository.findByScanIdOrderByCreatedAtDesc(scanId).stream()
                .filter(finding -> "COMPLETED".equalsIgnoreCase(finding.getAiEnrichmentStatus())
                        && finding.getAiEnrichedAt() != null)
                .map(Finding::getId)
                .forEach(tracker::markDone);

        eligibleIds.forEach(findingId -> enqueueFinding(findingId, scanId));
        long deadlineNanos = System.nanoTime() + scanWaitTimeoutMs(tier) * 1_000_000L;
        try {
            synchronized (activeJobsMonitor) {
                while (activeJobsByScan.containsKey(scanId)) {
                    try {
                        long remainingNanos = deadlineNanos - System.nanoTime();
                        if (remainingNanos <= 0) {
                            logger.warn("AI enrichment wait timed out for scan {} (tier={}); remaining requests will continue in background",
                                    scanId, tier);
                            break;
                        }
                        long millis = remainingNanos / 1_000_000L;
                        int nanos = (int) (remainingNanos % 1_000_000L);
                        activeJobsMonitor.wait(millis, nanos);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        logger.warn("Interrupted while waiting for AI enrichment to finish for scan {}", scanId);
                        return;
                    }
                }
            }
        } finally {
            scanProgress.remove(scanId, tracker);
        }
    }

    private long scanWaitTimeoutMs(ScanTier tier) {
        if (tier == null) return mediumScanWaitTimeoutMs;
        return switch (tier) {
            case FAST -> fastScanWaitTimeoutMs;
            case MEDIUM -> mediumScanWaitTimeoutMs;
            case DEEP -> deepScanWaitTimeoutMs;
        };
    }

    public void enrichFindingNow(Long findingId) {
        if (!isReadyToRun()) {
            return;
        }

        Finding finding = findingRepository.findWithContextById(findingId).orElse(null);
        if (finding == null || !isEligible(finding)) {
            return;
        }

        String fingerprint = buildFingerprint(finding);
        if (isCompletedForFingerprint(finding, fingerprint)) {
            return;
        }

        finding.setAiEnrichmentStatus("PROCESSING");
        finding.setAiPromptFingerprint(fingerprint);
        finding.setAiModel(huggingFaceInferenceClient.getResolvedModelId());
        finding.setAiEnrichmentError(null);
        findingRepository.save(finding);

        List<Finding> duplicateCandidates = findingRepository.findDuplicateCandidates(finding.getTarget().getId(), finding.getId(), PageRequest.of(0, 10));
        String candidatesString = duplicateCandidates.isEmpty() ? "None" : duplicateCandidates.stream()
                .map(c -> String.format("[ID: %d] %s (Severity: %s)", c.getId(), c.getTitle(), c.getSeverity()))
                .collect(Collectors.joining("\n"));

        Exception lastFailure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                AiInferenceClient.FindingAiEnrichmentResult enrichmentResult =
                        huggingFaceInferenceClient.enrichFinding(buildPrompt(finding, candidatesString));

                Finding persistedFinding = findingRepository.findWithContextById(findingId).orElse(finding);
                if (!fingerprint.equals(persistedFinding.getAiPromptFingerprint())) {
                    logger.debug("Skipping stale AI enrichment result for finding {}", findingId);
                    return;
                }
                persistedFinding.setAiDescription(sanitizeOutput(enrichmentResult.description(), 2500));
                persistedFinding.setExploitNarrative(sanitizeOutput(enrichmentResult.exploitNarrative(), 2500));
                persistedFinding.setAiSeverity(sanitizeOutput(enrichmentResult.aiSeverity(), 32));
                persistedFinding.setAiSeverityReason(sanitizeOutput(enrichmentResult.aiSeverityReason(), 2000));
                persistedFinding.setAiPriorityScore(enrichmentResult.aiPriorityScore());
                persistedFinding.setAiPriorityReason(sanitizeOutput(enrichmentResult.aiPriorityReason(), 2000));

                if (enrichmentResult.duplicateCandidateId() != null && enrichmentResult.duplicateCandidateId() > 0) {
                    boolean isValidDuplicate = duplicateCandidates.stream()
                            .anyMatch(c -> c.getId().equals(enrichmentResult.duplicateCandidateId().longValue()));
                    if (isValidDuplicate) {
                        Finding duplicateTarget = findingRepository.findById(enrichmentResult.duplicateCandidateId().longValue()).orElse(null);
                        if (duplicateTarget != null) {
                            persistedFinding.setAiDuplicateOf(duplicateTarget);
                        }
                    }
                }

                persistedFinding.setAiEnrichmentStatus("COMPLETED");
                persistedFinding.setAiModel(enrichmentResult.modelId());
                persistedFinding.setAiPromptFingerprint(fingerprint);
                persistedFinding.setAiEnrichedAt(LocalDateTime.now());
                persistedFinding.setAiEnrichmentError(null);
                Finding enrichedFinding = findingRepository.save(persistedFinding);

                if (enrichedFinding.getScan() != null) {
                    eventPublisher.publishScanEvent(enrichedFinding.getScan().getId(), "FINDING_ENRICHED", enrichedFinding);
                }
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                if (shouldRetryAiFailure(exception) && attempt < maxRetries) {
                    if (exception instanceof AiInferenceClient.RateLimitException rateLimitException) {
                        long delayMillis = rateLimitException.getRetryAfterMillis();
                        logger.warn("AI provider rate-limited finding {}; retrying in {} ms (attempt {} of {})",
                                findingId, delayMillis, attempt + 2, maxRetries + 1);
                        try {
                            Thread.sleep(delayMillis);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            lastFailure = interruptedException;
                            break;
                        }
                    } else {
                        long backoffMillis = Math.max(250L, 1000L << Math.min(5, attempt));
                        logger.warn("AI enrichment attempt {} failed for finding {}; retrying in {} ms after transient provider error",
                                attempt + 1, findingId, backoffMillis, exception);
                        try {
                            Thread.sleep(backoffMillis);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            lastFailure = interruptedException;
                            break;
                        }
                    }
                    continue;
                }

                logger.warn("AI enrichment attempt {} failed for finding {}; no retry scheduled for this failure type",
                        attempt + 1, findingId, exception);
                break;
            }
        }

        Finding failedFinding = findingRepository.findWithContextById(findingId).orElse(finding);
        if (!fingerprint.equals(failedFinding.getAiPromptFingerprint())) {
            logger.debug("Skipping stale AI enrichment failure for finding {}", findingId);
            return;
        }
        failedFinding.setAiEnrichmentStatus("FAILED");
        failedFinding.setAiModel(huggingFaceInferenceClient.getResolvedModelId());
        failedFinding.setAiPromptFingerprint(fingerprint);
        failedFinding.setAiEnrichedAt(null);
        failedFinding.setAiEnrichmentError(sanitizeOutput(lastFailure == null ? "Unknown enrichment error." : lastFailure.getMessage(), 1000));
        findingRepository.save(failedFinding);
    }

    private void markScanProgressDone(Long scanId, Long findingId) {
        ScanEnrichmentProgress tracker = scanProgress.get(scanId);
        if (tracker != null) {
            tracker.markDone(findingId);
        }
    }

    private static final class ScanEnrichmentProgress {
        private final int total;
        private final BiConsumer<Integer, Integer> listener;
        private final java.util.Set<Long> completedIds = ConcurrentHashMap.newKeySet();
        private final AtomicInteger completed = new AtomicInteger();

        private ScanEnrichmentProgress(int total, BiConsumer<Integer, Integer> listener) {
            this.total = total;
            this.listener = listener;
        }

        private void markDone(Long findingId) {
            if (findingId != null && completedIds.add(findingId)) {
                completed.incrementAndGet();
                publish();
            }
        }

        private void publish() {
            try {
                listener.accept(Math.min(completed.get(), total), total);
            } catch (RuntimeException exception) {
                logger.warn("Unable to publish AI enrichment progress", exception);
            }
        }
    }

    private boolean shouldRetryAiFailure(Throwable exception) {
        if (exception instanceof AiInferenceClient.RateLimitException) {
            return true;
        }

        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.LazyInitializationException) {
                return false;
            }
            if (current instanceof java.io.IOException || current instanceof java.net.SocketTimeoutException || current instanceof InterruptedException) {
                return true;
            }
            if (current instanceof IllegalStateException illegalStateException) {
                String message = illegalStateException.getMessage();
                if (message != null) {
                    String normalized = message.toLowerCase();
                    if (normalized.contains("groq response did not match")
                            || normalized.contains("bad request")
                            || normalized.contains("grok")
                            || normalized.contains("groq_api_keys")
                            || normalized.contains("empty response")
                            || normalized.contains("not match the required enrichment schema")) {
                        return false;
                    }
                    if (normalized.contains("unable to generate ai enrichment")
                            || normalized.contains("timeout")
                            || normalized.contains("tempor")
                            || normalized.contains("unavailable")
                            || normalized.contains("rate limit")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isEligible(Finding finding) {
        return finding != null
                && finding.getId() != null
                && finding.getScan() != null
                && finding.getTarget() != null
                && !"engine".equalsIgnoreCase(String.valueOf(finding.getToolName()))
                && !"execution".equalsIgnoreCase(String.valueOf(finding.getCategory()))
                && finding.getTitle() != null
                && !finding.getTitle().isBlank();
    }

    private boolean isCompletedForFingerprint(Finding finding, String fingerprint) {
        return "COMPLETED".equalsIgnoreCase(finding.getAiEnrichmentStatus())
                && fingerprint.equals(finding.getAiPromptFingerprint())
                && hasText(finding.getAiDescription())
                && hasText(finding.getExploitNarrative());
    }

    private AiInferenceClient.FindingAiPrompt buildPrompt(Finding finding, String candidatesString) {
        return new AiInferenceClient.FindingAiPrompt(
                sanitizeInput(finding.getToolName()),
                sanitizeInput(finding.getCategory()),
                sanitizeInput(finding.getTitle()),
                sanitizeInput(finding.getSeverity()),
                sanitizeInput(finding.getAffectedUrl()),
                sanitizeInput(finding.getDescription()),
                sanitizeInput(finding.getEvidenceData()),
                sanitizeInput(finding.getCweId()),
                sanitizeInput(finding.getOwaspCategory()),
                candidatesString
        );
    }

    private String buildFingerprint(Finding finding) {
        String fingerprintSource = String.join("|",
                sanitizeInput(finding.getToolName()),
                sanitizeInput(finding.getCategory()),
                sanitizeInput(finding.getTitle()),
                sanitizeInput(finding.getSeverity()),
                sanitizeInput(finding.getAffectedUrl()),
                sanitizeInput(finding.getDescription()),
                sanitizeInput(finding.getEvidenceData()),
                sanitizeInput(finding.getCweId()),
                sanitizeInput(finding.getOwaspCategory())
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprintSource.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            return Integer.toHexString(Objects.hash(fingerprintSource));
        }
    }

    private String sanitizeInput(String value) {
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.length() <= maxInputChars) {
            return normalizedValue;
        }

        return normalizedValue.substring(0, maxInputChars) + "…";
    }

    private String sanitizeOutput(String value, int maxLength) {
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.isBlank()) {
            return null;
        }
        if (normalizedValue.length() <= maxLength) {
            return normalizedValue;
        }
        return normalizedValue.substring(0, maxLength) + "…";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
