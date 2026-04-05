package com.scanner.app.service;

import com.scanner.app.domain.Finding;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.websocket.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class FindingEnrichmentService {
    private static final Logger logger = LoggerFactory.getLogger(FindingEnrichmentService.class);

    private final FindingRepository findingRepository;
    private final HuggingFaceInferenceClient huggingFaceInferenceClient;
    private final EventPublisher eventPublisher;
    private final Executor findingEnrichmentExecutor;
    private final boolean enrichmentEnabled;
    private final int maxInputChars;
    private final int maxRetries;

    public FindingEnrichmentService(
            FindingRepository findingRepository,
            HuggingFaceInferenceClient huggingFaceInferenceClient,
            EventPublisher eventPublisher,
            @Qualifier("findingEnrichmentExecutor") Executor findingEnrichmentExecutor,
            @Value("${app.ai.finding-enrichment.enabled:true}") boolean enrichmentEnabled,
            @Value("${app.ai.finding-enrichment.max-input-chars:6000}") int maxInputChars,
            @Value("${app.ai.finding-enrichment.max-retries:1}") int maxRetries
    ) {
        this.findingRepository = findingRepository;
        this.huggingFaceInferenceClient = huggingFaceInferenceClient;
        this.eventPublisher = eventPublisher;
        this.findingEnrichmentExecutor = findingEnrichmentExecutor;
        this.enrichmentEnabled = enrichmentEnabled;
        this.maxInputChars = Math.max(500, maxInputChars);
        this.maxRetries = Math.max(0, maxRetries);
    }

    public void requestEnrichment(Finding finding) {
        if (!isEligible(finding) || !isReadyToRun()) {
            return;
        }

        Finding persistedFinding = findingRepository.findWithContextById(finding.getId()).orElse(null);
        if (persistedFinding == null || !isEligible(persistedFinding)) {
            return;
        }

        String fingerprint = buildFingerprint(persistedFinding);
        if (isCompletedForFingerprint(persistedFinding, fingerprint)) {
            return;
        }

        if ("PROCESSING".equalsIgnoreCase(persistedFinding.getAiEnrichmentStatus())
                && fingerprint.equals(persistedFinding.getAiPromptFingerprint())) {
            return;
        }

        persistedFinding.setAiPromptFingerprint(fingerprint);
        persistedFinding.setAiEnrichmentStatus("PENDING");
        persistedFinding.setAiModel(huggingFaceInferenceClient.getResolvedModelId());
        persistedFinding.setAiEnrichmentError(null);
        persistedFinding.setAiEnrichedAt(null);
        persistedFinding.setAiDescription(null);
        persistedFinding.setExploitNarrative(null);
        findingRepository.save(persistedFinding);

        CompletableFuture.runAsync(() -> enrichFindingNow(persistedFinding.getId()), findingEnrichmentExecutor)
                .exceptionally(exception -> {
                    logger.warn("Failed to schedule AI enrichment for finding {}", persistedFinding.getId(), exception);
                    return null;
                });
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

        Exception lastFailure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HuggingFaceInferenceClient.FindingAiEnrichmentResult enrichmentResult =
                        huggingFaceInferenceClient.enrichFinding(buildPrompt(finding));

                Finding persistedFinding = findingRepository.findWithContextById(findingId).orElse(finding);
                if (!fingerprint.equals(persistedFinding.getAiPromptFingerprint())) {
                    logger.debug("Skipping stale AI enrichment result for finding {}", findingId);
                    return;
                }
                persistedFinding.setAiDescription(sanitizeOutput(enrichmentResult.description(), 2500));
                persistedFinding.setExploitNarrative(sanitizeOutput(enrichmentResult.exploitNarrative(), 2500));
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
                logger.warn("AI enrichment attempt {} failed for finding {}", attempt + 1, findingId, exception);
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

    private boolean isReadyToRun() {
        return enrichmentEnabled && huggingFaceInferenceClient.isConfigured();
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

    private HuggingFaceInferenceClient.FindingAiPrompt buildPrompt(Finding finding) {
        return new HuggingFaceInferenceClient.FindingAiPrompt(
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
