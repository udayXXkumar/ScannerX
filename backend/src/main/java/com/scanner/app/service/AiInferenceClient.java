package com.scanner.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiInferenceClient {

    private static final Logger logger = LoggerFactory.getLogger(AiInferenceClient.class);
    private static final URI GROQ_CHAT_COMPLETIONS_URI = URI.create("https://api.groq.com/openai/v1/chat/completions");

    private final ObjectMapper objectMapper;
    private final GroqApiKeyPool keyPool;
    private final GroqProperties groqProperties;
    private final GroqRequestConcurrencyLimiter concurrencyLimiter;
    private final String modelId;
    private final Duration timeout;
    private final HttpClient httpClient;

    public AiInferenceClient(
            ObjectMapper objectMapper,
            GroqApiKeyPool keyPool,
            GroqProperties groqProperties,
            GroqRequestConcurrencyLimiter concurrencyLimiter,
            @Value("${app.ai.groq.model-id:openai/gpt-oss-20b}") String groqModelId
    ) {
        this.objectMapper = objectMapper;
        this.keyPool = keyPool;
        this.groqProperties = groqProperties;
        this.concurrencyLimiter = concurrencyLimiter;

        List<String> rawKeys = groqProperties == null ? List.of() : groqProperties.getApiKeys();
        this.keyPool.initialize(rawKeys);

        this.modelId = groqModelId == null || groqModelId.isBlank() ? "openai/gpt-oss-20b" : groqModelId.trim();
        long configuredTimeoutMs = groqProperties == null ? 15000L : Math.max(1000L, groqProperties.getRequestTimeoutMs());
        this.timeout = Duration.ofMillis(configuredTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        logger.info("Groq inference configuration: keysConfigured={}, model={}, maxConcurrentRequests={}, requestTimeoutMs={}",
                this.keyPool.size(), this.modelId, groqProperties == null ? 1 : groqProperties.getMaxConcurrentRequests(), configuredTimeoutMs);
    }

    public boolean isConfigured() {
        return keyPool.size() > 0;
    }

    public String getResolvedModelId() {
        return modelId;
    }

    public FindingAiEnrichmentResult enrichFinding(FindingAiPrompt findingPrompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("GROQ_API_KEYS is not configured.");
        }

        try {
            String responseBody = sendChatCompletionRequest(findingPrompt);
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new IllegalStateException("Groq returned an empty response.");
            }

            JsonNode contentJson = objectMapper.readTree(content);
            String description = contentJson.path("description").asText("").trim();
            String exploitNarrative = contentJson.path("exploitNarrative").asText("").trim();
            String aiSeverity = contentJson.path("aiSeverity").asText("").trim();
            String aiSeverityReason = contentJson.path("aiSeverityReason").asText("").trim();
            int aiPriorityScore = contentJson.path("aiPriorityScore").asInt(-1);
            String aiPriorityReason = contentJson.path("aiPriorityReason").asText("").trim();
            int duplicateCandidateId = contentJson.path("duplicateCandidateId").asInt(-1);

            if (description.isBlank() || exploitNarrative.isBlank()) {
                throw new IllegalStateException("Groq response did not match the required enrichment schema.");
            }

            String responseModel = root.path("model").asText(getResolvedModelId());
            return new FindingAiEnrichmentResult(description, exploitNarrative, aiSeverity, aiSeverityReason, aiPriorityScore, aiPriorityReason, duplicateCandidateId, responseModel);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Unable to generate AI enrichment for finding.", exception);
        }
    }

    private String sendChatCompletionRequest(FindingAiPrompt findingPrompt) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", getResolvedModelId());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", userPrompt(findingPrompt))
        ));
        payload.put("temperature", 0.2);
        payload.put("max_completion_tokens", 800);
        payload.put("stream", false);
        payload.put("response_format", responseFormat());
        payload.put("include_reasoning", false);

        String payloadJson = objectMapper.writeValueAsString(payload);
        int maxAttempts = Math.max(1, Math.min(20, keyPool.size() * 3 + groqProperties.getMaxRetries()));
        long requestDeadlineMs = System.currentTimeMillis() + Math.max(30_000L, timeout.toMillis() * 3L);
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            GroqApiKeyState selectedKeyNode = keyPool.acquireBestKey();

            if (selectedKeyNode == null) {
                long earliestAvailableAt = keyPool.getEarliestAvailableTime();
                long now = System.currentTimeMillis();
                long waitTime = Math.max(0L, earliestAvailableAt - now);
                long remainingBudget = Math.max(0L, requestDeadlineMs - now);
                if (remainingBudget <= 0L && waitTime <= 0L) {
                    throw new RateLimitException("All AI API keys are currently unavailable.", 0L);
                }

                long sleepTime = remainingBudget > 0L ? Math.min(waitTime, remainingBudget) : waitTime;
                sleepTime = Math.max(250L, sleepTime);
                logger.warn("[GroqClient] All keys are unavailable. Earliest retry in {} ms.", sleepTime);
                Thread.sleep(Math.min(sleepTime, remainingBudget > 0L ? remainingBudget : sleepTime));
                continue;
            }

            try (GroqRequestConcurrencyLimiter.AcquiredRequest ignored = concurrencyLimiter.acquire()) {
                HttpRequest request = HttpRequest.newBuilder(GROQ_CHAT_COMPLETIONS_URI)
                        .timeout(timeout)
                        .header("Authorization", "Bearer " + selectedKeyNode.getKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                        .build();

                HttpResponse<String> response;
                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException ioException) {
                    selectedKeyNode.recordFailure(false, false);
                    logger.warn("[GroqClient] network failure for key {}: {}", selectedKeyNode.getMaskedKey(), ioException.getMessage());
                    long backoff = computeBackoff(attempt, 0);
                    if (backoff > 0L) {
                        Thread.sleep(backoff);
                    }
                    continue;
                }

                if (response.statusCode() >= 400) {
                    String serviceMessage = extractServiceError(response.body());
                    GroqErrorClassification classification = classifyError(response.statusCode(), response.headers(), serviceMessage);

                    switch (classification) {
                        case UNAUTHORIZED, FORBIDDEN -> {
                            logger.error("[GroqClient] key {} rejected ({}); disabling it and retrying with another key.",
                                    selectedKeyNode.getMaskedKey(), response.statusCode());
                            selectedKeyNode.recordFailure(false, true);
                            continue;
                        }
                        case RATE_LIMITED -> {
                            long delayMillis = parseGroqRateLimitDelay(response, serviceMessage);
                            selectedKeyNode.recordFailure(true, false);
                            selectedKeyNode.setCooldownUntil(System.currentTimeMillis() + delayMillis);
                            selectedKeyNode.setEstimatedAvailability(0);
                            logger.warn("[GroqClient] key {} received 429; cooldown={} ms.",
                                    selectedKeyNode.getMaskedKey(), delayMillis);
                            continue;
                        }
                        case SERVER_ERROR, NETWORK, TIMEOUT -> {
                            long backoff = computeBackoff(attempt, response.statusCode());
                            selectedKeyNode.recordFailure(false, false);
                            logger.warn("[GroqClient] key {} transient failure ({}); retrying with backoff {} ms.",
                                    selectedKeyNode.getMaskedKey(), response.statusCode(), backoff);
                            if (backoff > 0L) {
                                Thread.sleep(backoff);
                            }
                            continue;
                        }
                        case BAD_REQUEST -> {
                            logger.error("[GroqClient] 400 Bad Request sent to key {}. Aborting retries. Message: {}",
                                    selectedKeyNode.getMaskedKey(), serviceMessage);
                            throw new IllegalStateException("Groq 400 Bad Request: " + serviceMessage);
                        }
                        default -> {
                            selectedKeyNode.recordFailure(false, false);
                            throw new IllegalStateException("Groq request failed with status " + response.statusCode() + ": " + serviceMessage);
                        }
                    }
                }

                selectedKeyNode.recordSuccess();
                applyRateLimitMetadata(selectedKeyNode, response);
                return response.body();
            } finally {
                if (selectedKeyNode != null) {
                    selectedKeyNode.decrementInFlight();
                }
            }
        }

        throw new IllegalStateException("Exhausted all API keys and retry attempts due to failures or rate limits.");
    }

    private String extractServiceError(String responseBody) {
        String message = "";
        try {
            JsonNode errorJson = objectMapper.readTree(responseBody);
            JsonNode error = errorJson.path("error");
            if (error.isObject()) {
                message = error.path("message").asText("");
            } else if (error.isTextual()) {
                message = error.asText();
            }
            if (message.isBlank()) {
                message = errorJson.path("message").asText("");
            }
        } catch (Exception ignored) {
        }
        
        if (message.isBlank()) {
            message = responseBody == null ? "" : responseBody.trim();
        }
        
        for (GroqApiKeyState node : keyPool.getAllKeys()) {
            if (!node.getKey().isBlank()) {
                message = message.replace(node.getKey(), "<redacted>");
            }
        }
        
        message = message.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        if (message.length() > 400) {
            message = message.substring(0, 400) + "…";
        }
        return message;
    }

    static long parseResetDurationToMillis(String rawDuration) {
        if (rawDuration == null) {
            return -1L;
        }

        String normalized = rawDuration.trim();
        if (normalized.isEmpty()) {
            return -1L;
        }

        String candidate = normalized.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (candidate.matches("\\d+(?:\\.\\d+)?")) {
            return Math.round(Double.parseDouble(candidate) * 1000.0);
        }

        Matcher matcher = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+(?:\\.\\d+)?)s)?(?:(\\d+)ms)?")
                .matcher(candidate);
        if (!matcher.matches()) {
            return -1L;
        }

        long hours = parseMatcherGroup(matcher, 1);
        long minutes = parseMatcherGroup(matcher, 2);
        double seconds = parseMatcherDouble(matcher, 3);
        long millis = parseMatcherGroup(matcher, 4);

        if (candidate.endsWith("ms") && matcher.group(4) != null) {
            return millis;
        }

        return Math.round((hours * 3_600_000L) + (minutes * 60_000L) + (seconds * 1000.0) + millis);
    }

    private static long parseMatcherGroup(Matcher matcher, int groupIndex) {
        String value = matcher.group(groupIndex);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    private static double parseMatcherDouble(Matcher matcher, int groupIndex) {
        String value = matcher.group(groupIndex);
        if (value == null || value.isBlank()) {
            return 0.0d;
        }
        return Double.parseDouble(value);
    }

    private long parseGroqRateLimitDelay(HttpResponse<?> response, String serviceMessage) {
        String resetStr = response.headers().firstValue("x-ratelimit-reset-requests")
                .or(() -> response.headers().firstValue("x-ratelimit-reset-tokens"))
                .orElse("").trim();
        if (!resetStr.isBlank()) {
            long parsed = parseResetDurationToMillis(resetStr);
            if (parsed >= 0L) {
                return boundedRetryDelay(parsed);
            }
        }

        String retryAfter = response.headers().firstValue("Retry-After").orElse("").trim();
        if (!retryAfter.isBlank()) {
            long parsed = parseResetDurationToMillis(retryAfter);
            if (parsed >= 0L) {
                return boundedRetryDelay(parsed);
            }
        }

        Matcher matcher = Pattern.compile("try again in\\s+([0-9]+(?:\\.[0-9]+)?\\s*(?:ms|s|m|h))", Pattern.CASE_INSENSITIVE)
                .matcher(serviceMessage);
        if (matcher.find()) {
            long parsed = parseResetDurationToMillis(matcher.group(1));
            if (parsed >= 0L) {
                return boundedRetryDelay(parsed);
            }
        }

        return 2000L;
    }

    private long boundedRetryDelay(long requestedMillis) {
        return Math.max(250L, Math.min(groqProperties.getMaxBackoffMs(), requestedMillis));
    }

    private long computeBackoff(int attempt, int statusCode) {
        long base = Math.min(groqProperties.getMaxBackoffMs(), groqProperties.getRetryBackoffMs() * (long) Math.pow(2, Math.max(0, attempt - 1)));
        if (statusCode >= 500) {
            return base;
        }
        return Math.min(groqProperties.getMaxBackoffMs(), base / 2);
    }

    private void applyRateLimitMetadata(GroqApiKeyState keyState, HttpResponse<String> response) {
        response.headers().firstValue("x-ratelimit-remaining-requests").ifPresent(value -> {
            try {
                int remaining = Integer.parseInt(value);
                keyState.setEstimatedAvailability(Math.max(0, Math.min(100, remaining)));
            } catch (NumberFormatException ignored) {
            }
        });
        response.headers().firstValue("x-ratelimit-reset-requests").ifPresent(value -> {
            long delayMillis = parseResetDurationToMillis(value);
            if (delayMillis >= 0L) {
                keyState.setRateLimitResetAt(System.currentTimeMillis() + delayMillis);
            }
        });
    }

    private GroqErrorClassification classifyError(int statusCode, HttpHeaders headers, String serviceMessage) {
        if (statusCode == 401) {
            return GroqErrorClassification.UNAUTHORIZED;
        }
        if (statusCode == 403) {
            return GroqErrorClassification.FORBIDDEN;
        }
        if (statusCode == 429) {
            return GroqErrorClassification.RATE_LIMITED;
        }
        if (statusCode == 400) {
            return GroqErrorClassification.BAD_REQUEST;
        }
        if (statusCode == 408) {
            return GroqErrorClassification.TIMEOUT;
        }
        if (statusCode >= 500) {
            return GroqErrorClassification.SERVER_ERROR;
        }
        if (statusCode == 0) {
            return GroqErrorClassification.NETWORK;
        }
        if (serviceMessage != null && serviceMessage.toLowerCase().contains("timeout")) {
            return GroqErrorClassification.TIMEOUT;
        }
        return GroqErrorClassification.APPLICATION;
    }

    private enum GroqErrorClassification {
        UNAUTHORIZED,
        FORBIDDEN,
        RATE_LIMITED,
        BAD_REQUEST,
        TIMEOUT,
        SERVER_ERROR,
        NETWORK,
        SERIALIZATION,
        APPLICATION
    }

    public static final class RateLimitException extends RuntimeException {
        private final long retryAfterMillis;

        public RateLimitException(String message, long retryAfterMillis) {
            super(message);
            this.retryAfterMillis = retryAfterMillis;
        }

        public long getRetryAfterMillis() {
            return retryAfterMillis;
        }
    }

    private Map<String, Object> responseFormat() {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "ScannerXFindingEnrichment",
                        "strict", true,
                        "schema", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "description", Map.of(
                                                "type", "string",
                                                "description", "Human-readable finding summary that combines attacker relevance with defender context."
                                        ),
                                        "exploitNarrative", Map.of(
                                                "type", "string",
                                                "description", "High-level attacker-perspective and defender-response explanation without payloads or step-by-step instructions."
                                        ),
                                        "aiSeverity", Map.of(
                                                "type", "string",
                                                "enum", List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"),
                                                "description", "AI-assigned or validated severity."
                                        ),
                                        "aiSeverityReason", Map.of(
                                                "type", "string",
                                                "description", "Reason for the assigned severity."
                                        ),
                                        "aiPriorityScore", Map.of(
                                                "type", "integer",
                                                "description", "Impact priority score (1-10)."
                                        ),
                                        "aiPriorityReason", Map.of(
                                                "type", "string",
                                                "description", "Reason for the priority score."
                                        ),
                                        "duplicateCandidateId", Map.of(
                                                "type", "integer",
                                                "description", "If this finding is a semantic duplicate of a provided candidate, output its ID. Otherwise output -1."
                                        )
                                ),
                                "required", List.of("description", "exploitNarrative", "aiSeverity", "aiSeverityReason", "aiPriorityScore", "aiPriorityReason", "duplicateCandidateId")
                        )
                )
        );
    }

    private String systemPrompt() {
        return "You are a senior web security analyst helping teams understand scanner findings from both attacker and defender perspectives.\n" +
               "Return valid JSON only.\n" +
               "Write concise, accurate, professional explanations.\n" +
               "Never include exploit payloads, commands, shell snippets, bypass instructions, or step-by-step attack walkthroughs.\n" +
               "The exploit narrative must stay safe and high level.\n" +
               "Explain why an attacker would care about the weakness, what conditions make it dangerous, and what the likely impact is.\n" +
               "Also keep the defender's viewpoint present by clarifying what security teams should prioritize or validate next.\n";
    }

    private String userPrompt(FindingAiPrompt prompt) throws IOException {
        Map<String, Object> findingPayload = new LinkedHashMap<>();
        findingPayload.put("toolName", prompt.toolName());
        findingPayload.put("category", prompt.category());
        findingPayload.put("title", prompt.title());
        findingPayload.put("severity", prompt.severity());
        findingPayload.put("affectedUrl", prompt.affectedUrl());
        findingPayload.put("description", prompt.description());
        findingPayload.put("evidence", prompt.evidence());
        findingPayload.put("cweId", prompt.cweId());
        findingPayload.put("owaspCategory", prompt.owaspCategory());

        return "Analyze this finding for ScannerX and provide the requested fields.\n" +
               "If duplicate candidates are provided, evaluate if this finding is a semantic duplicate of any candidate (describing the same underlying issue on the same target).\n" +
               "Produce:\n" +
               "1. description: a clear analyst-friendly explanation of what the finding means in context.\n" +
               "2. exploitNarrative: a high-level attacker-perspective explanation combined with what the defender should consider next.\n" +
               "3. aiSeverity: valid severity level.\n" +
               "4. aiSeverityReason: why this severity applies.\n" +
               "5. aiPriorityScore: 1-10 score indicating impact/priority.\n" +
               "6. aiPriorityReason: why this score applies.\n" +
               "7. duplicateCandidateId: the ID of a semantic duplicate from the candidates list, or -1.\n\n" +
               "Finding JSON:\n" + objectMapper.writeValueAsString(findingPayload) + "\n\n" +
               "Duplicate Candidates:\n" + prompt.duplicateCandidates();
    }

    public record FindingAiPrompt(
            String toolName,
            String category,
            String title,
            String severity,
            String affectedUrl,
            String description,
            String evidence,
            String cweId,
            String owaspCategory,
            String duplicateCandidates
    ) {
    }

    public record FindingAiEnrichmentResult(
            String description,
            String exploitNarrative,
            String aiSeverity,
            String aiSeverityReason,
            Integer aiPriorityScore,
            String aiPriorityReason,
            Integer duplicateCandidateId,
            String modelId
    ) {
    }
}
