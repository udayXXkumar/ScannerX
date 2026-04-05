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

@Service
public class HuggingFaceInferenceClient {
    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceInferenceClient.class);
    private static final URI CHAT_COMPLETIONS_URI = URI.create("https://router.huggingface.co/v1/chat/completions");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiToken;
    private final String modelId;
    private final String provider;
    private final Duration timeout;

    public HuggingFaceInferenceClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.hf.api-token:}") String apiToken,
            @Value("${app.ai.hf.model-id:Qwen/Qwen2.5-7B-Instruct}") String modelId,
            @Value("${app.ai.hf.provider:}") String provider,
            @Value("${app.ai.finding-enrichment.timeout-ms:15000}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.modelId = modelId == null || modelId.isBlank() ? "Qwen/Qwen2.5-7B-Instruct" : modelId.trim();
        this.provider = provider == null ? "" : provider.trim();
        this.timeout = Duration.ofMillis(Math.max(1000L, timeoutMs));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean isConfigured() {
        return !apiToken.isBlank();
    }

    public String getResolvedModelId() {
        if (provider.isBlank()) {
            return modelId;
        }

        if (modelId.endsWith(":" + provider)) {
            return modelId;
        }

        return modelId + ":" + provider;
    }

    public FindingAiEnrichmentResult enrichFinding(FindingAiPrompt findingPrompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("Hugging Face token is not configured.");
        }

        try {
            String responseBody = sendChatCompletionRequest(findingPrompt);
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new IllegalStateException("Hugging Face returned an empty response.");
            }

            JsonNode contentJson = objectMapper.readTree(content);
            String description = contentJson.path("description").asText("").trim();
            String exploitNarrative = contentJson.path("exploitNarrative").asText("").trim();

            if (description.isBlank() || exploitNarrative.isBlank()) {
                throw new IllegalStateException("Hugging Face response did not match the required enrichment schema.");
            }

            String responseModel = root.path("model").asText(getResolvedModelId());
            return new FindingAiEnrichmentResult(description, exploitNarrative, responseModel);
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
        payload.put("max_tokens", 420);
        payload.put("stream", false);
        payload.put("response_format", responseFormat());

        HttpRequest request = HttpRequest.newBuilder(CHAT_COMPLETIONS_URI)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            logger.warn("Hugging Face enrichment request failed with status {} and body {}", response.statusCode(), response.body());
            throw new IllegalStateException("Hugging Face request failed with status " + response.statusCode() + ".");
        }

        return response.body();
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
                                        )
                                ),
                                "required", List.of("description", "exploitNarrative")
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
                Summarize this finding for ScannerX.
                Produce:
                1. description: a clear analyst-friendly explanation of what the finding means in context, why it matters to an attacker, and what defenders should understand immediately.
                2. exploitNarrative: a high-level attacker-perspective explanation of how this kind of weakness can create opportunity, combined with what the defender should consider next. Focus on prerequisites, attacker opportunity, likely impact, and defensive implications only.

                Do not mention that you are an AI model.
                Do not provide payloads, commands, reproduction steps, or tool instructions.
                Keep the output factual, suitable for triage and reporting, and safe to share in a security operations workflow.

                Finding JSON:
                """ + objectMapper.writeValueAsString(findingPayload);
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
            String owaspCategory
    ) {
    }

    public record FindingAiEnrichmentResult(
            String description,
            String exploitNarrative,
            String modelId
    ) {
    }
}
