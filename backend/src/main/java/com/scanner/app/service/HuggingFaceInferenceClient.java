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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Comparator;

@Service
public class HuggingFaceInferenceClient {

    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceInferenceClient.class);
    private static final URI HUGGING_FACE_CHAT_COMPLETIONS_URI = URI.create("https://router.huggingface.co/v1/chat/completions");

    private final ObjectMapper objectMapper;
    private final List<ApiKeyNode> apiKeys;
    private final String modelId;
    private final String aiProvider;
    private final String hfProvider;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final URI chatCompletionsUri;

    private static class ApiKeyNode {
        final String key;
        volatile long availableAt;
        volatile long lastUsedAt;

        ApiKeyNode(String key) {
            this.key = key;
            this.availableAt = 0;
            this.lastUsedAt = 0;
        }
    }

    public HuggingFaceInferenceClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.provider:huggingface}") String aiProvider,
            @Value("${app.ai.hf.api-token:}") String hfApiToken,
            @Value("${app.ai.hf.model-id:Qwen/Qwen2.5-7B-Instruct}") String hfModelId,
            @Value("${app.ai.hf.provider:}") String hfProvider,
            @Value("${app.ai.finding-enrichment.timeout-ms:15000}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        String normalizedProvider = aiProvider == null || aiProvider.isBlank() ? "huggingface" : aiProvider.trim().toLowerCase();
        if (!"huggingface".equals(normalizedProvider) && !"groq".equals(normalizedProvider)) {
            throw new IllegalArgumentException("Unsupported AI_PROVIDER '" + normalizedProvider + "'. Supported values: huggingface, groq.");
        }
        this.aiProvider = "huggingface";
        this.hfProvider = hfProvider == null ? "" : hfProvider.trim();

        List<String> selectedKeys = Arrays.stream((hfApiToken == null ? "" : hfApiToken).split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .toList();

        this.apiKeys = selectedKeys.stream()
                .map(ApiKeyNode::new)
                .collect(Collectors.toList());

        this.modelId = hfModelId == null || hfModelId.isBlank() ? "Qwen/Qwen2.5-7B-Instruct" : hfModelId.trim();
        this.chatCompletionsUri = HUGGING_FACE_CHAT_COMPLETIONS_URI;
        this.timeout = Duration.ofMillis(Math.max(1000L, timeoutMs));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        logger.info("AI inference configuration: provider={}, keysConfigured={}, model={}, providerOverrideConfigured={}",
                this.aiProvider, this.apiKeys.size(), this.modelId, !this.hfProvider.isBlank());
    }

    public boolean isConfigured() {
        return !apiKeys.isEmpty();
    }

    public String getResolvedModelId() {
        if (!aiProvider.equals("huggingface") || hfProvider.isBlank()) {
            return modelId;
        }

        if (modelId.endsWith(":" + hfProvider)) {
            return modelId;
        }

        return modelId + ":" + hfProvider;
    }

    public FindingAiEnrichmentResult enrichFinding(FindingAiPrompt findingPrompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("HF_API_TOKEN is not configured.");
        }

        try {
            String responseBody = sendChatCompletionRequest(findingPrompt);
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new IllegalStateException(aiProvider + " returned an empty response.");
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
                throw new IllegalStateException(aiProvider + " response did not match the required enrichment schema.");
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
        payload.put("max_tokens", 800);
        payload.put("stream", false);
        payload.put("response_format", responseFormat());

        String payloadJson = objectMapper.writeValueAsString(payload);
        int maxAttempts = Math.max(1, apiKeys.size() * 2);
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            ApiKeyNode selectedKeyNode = null;
            long now = System.currentTimeMillis();

            synchronized (apiKeys) {
                List<ApiKeyNode> availableKeys = apiKeys.stream()
                        .filter(node -> node.availableAt <= now)
                        .collect(Collectors.toList());

                if (!availableKeys.isEmpty()) {
                    selectedKeyNode = availableKeys.stream()
                            .min(Comparator.comparingLong(node -> node.lastUsedAt))
                            .orElse(availableKeys.get(0));
                    selectedKeyNode.lastUsedAt = now;
                } else {
                    ApiKeyNode soonest = apiKeys.stream()
                            .min(Comparator.comparingLong(node -> node.availableAt))
                            .orElseThrow();
                    long waitTime = soonest.availableAt - now;
                    throw new RateLimitException("All AI API keys are currently rate-limited.", waitTime);
                }
            }

            HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + selectedKeyNode.key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                String serviceMessage = extractServiceError(response.body());
                if (response.statusCode() == 429) {
                    long delayMillis = retryDelayMillis(response, serviceMessage);
                    selectedKeyNode.availableAt = System.currentTimeMillis() + delayMillis;
                    logger.warn("{} enrichment request rate-limited on a key (delay: {}ms). Retrying with another key if available...", aiProvider, delayMillis);
                    continue;
                }
                
                throw new IllegalStateException(aiProvider + " request failed with status " + response.statusCode()
                        + (serviceMessage.isBlank() ? "." : ": " + serviceMessage));
            }
            return response.body();
        }
        
        throw new IllegalStateException("Exhausted all API keys and retry attempts due to rate limits.");
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
        } catch (IOException ignored) {
            // Some gateway errors are plain text rather than JSON.
        }

        if (message.isBlank()) {
            message = responseBody == null ? "" : responseBody.trim();
        }
        for (ApiKeyNode node : apiKeys) {
            if (!node.key.isBlank()) {
                message = message.replace(node.key, "<redacted>");
            }
        }
        message = message.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        if (message.length() > 400) {
            message = message.substring(0, 400) + "…";
        }
        return message;
    }

    private long retryDelayMillis(HttpResponse<?> response, String serviceMessage) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse("").trim();
        if (!retryAfter.isBlank()) {
            try {
                double seconds = Double.parseDouble(retryAfter);
                return boundedRetryDelay((long) Math.ceil(seconds * 1000));
            } catch (NumberFormatException ignored) {
                // Groq may provide the suggested delay in its error message instead.
            }
        }

        Matcher matcher = Pattern.compile("try again in\\s+([0-9]+(?:\\.[0-9]+)?)\\s*s", Pattern.CASE_INSENSITIVE)
                .matcher(serviceMessage);
        if (matcher.find()) {
            try {
                return boundedRetryDelay((long) Math.ceil(Double.parseDouble(matcher.group(1)) * 1000));
            } catch (NumberFormatException ignored) {
                // Fall through to the short default delay.
            }
        }
        return 1000;
    }

    private long boundedRetryDelay(long requestedMillis) {
        return Math.max(250, Math.min(60_000, requestedMillis));
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
        return """
                You are a senior web security analyst helping teams understand scanner findings from both attacker and defender perspectives.
                Return valid JSON only.
                Write concise, accurate, professional explanations.
                Never include exploit payloads, commands, shell snippets, bypass instructions, or step-by-step attack walkthroughs.
                The exploit narrative must stay safe and high level.
                Explain why an attacker would care about the weakness, what conditions make it dangerous, and what the likely impact is.
                Also keep the defender's viewpoint present by clarifying what security teams should prioritize or validate next.
                """;
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

        return """
                Analyze this finding for ScannerX and provide the requested fields.
                If duplicate candidates are provided, evaluate if this finding is a semantic duplicate of any candidate (describing the same underlying issue on the same target).
                Produce:
                1. description: a clear analyst-friendly explanation of what the finding means in context.
                2. exploitNarrative: a high-level attacker-perspective explanation combined with what the defender should consider next.
                3. aiSeverity: valid severity level.
                4. aiSeverityReason: why this severity applies.
                5. aiPriorityScore: 1-10 score indicating impact/priority.
                6. aiPriorityReason: why this score applies.
                7. duplicateCandidateId: the ID of a semantic duplicate from the candidates list, or -1.

                Finding JSON:
                """ + objectMapper.writeValueAsString(findingPayload) + """

                Duplicate Candidates:
                """ + prompt.duplicateCandidates();
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
