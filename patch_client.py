import re

with open("backend/src/main/java/com/scanner/app/service/HuggingFaceInferenceClient.java", "r") as f:
    content = f.read()

# Add imports
imports = """import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Comparator;"""

content = content.replace("import java.util.regex.Pattern;", "import java.util.regex.Pattern;\n" + imports)

# Replace fields
old_fields = """    private final ObjectMapper objectMapper;
    private final String apiToken;
    private final String modelId;
    private final String aiProvider;
    private final String hfProvider;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final URI chatCompletionsUri;"""

new_fields = """    private final ObjectMapper objectMapper;
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
    }"""
content = content.replace(old_fields, new_fields)

# Replace Constructor args and logic
old_constructor = """    public HuggingFaceInferenceClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.provider:huggingface}") String aiProvider,
            @Value("${app.ai.hf.api-token:}") String hfApiToken,
            @Value("${app.ai.hf.model-id:Qwen/Qwen2.5-7B-Instruct}") String hfModelId,
            @Value("${app.ai.hf.provider:}") String hfProvider,
            @Value("${app.ai.groq.api-key:}") String groqApiKey,
            @Value("${app.ai.groq.model-id:openai/gpt-oss-20b}") String groqModelId,
            @Value("${app.ai.finding-enrichment.timeout-ms:15000}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.aiProvider = aiProvider == null || aiProvider.isBlank() ? "huggingface" : aiProvider.trim().toLowerCase();
        this.hfProvider = hfProvider == null ? "" : hfProvider.trim();
        if (!this.aiProvider.equals("huggingface") && !this.aiProvider.equals("groq")) {
            throw new IllegalArgumentException("Unsupported AI_PROVIDER '" + this.aiProvider + "'. Supported values: huggingface, groq.");
        }
        this.apiToken = valueForSelectedProvider(hfApiToken, groqApiKey);
        String configuredModelId = this.aiProvider.equals("groq") ? groqModelId : hfModelId;
        String defaultModelId = this.aiProvider.equals("groq") ? "openai/gpt-oss-20b" : "Qwen/Qwen2.5-7B-Instruct";
        this.modelId = configuredModelId == null || configuredModelId.isBlank() ? defaultModelId : configuredModelId.trim();
        this.chatCompletionsUri = this.aiProvider.equals("groq") ? GROQ_CHAT_COMPLETIONS_URI : HUGGING_FACE_CHAT_COMPLETIONS_URI;
        this.timeout = Duration.ofMillis(Math.max(1000L, timeoutMs));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        logger.info("AI inference configuration: provider={}, tokenConfigured={}, model={}, providerOverrideConfigured={}",
                this.aiProvider, isConfigured(), this.modelId, !this.hfProvider.isBlank());
    }

    private String valueForSelectedProvider(String hfApiToken, String groqApiKey) {
        String selectedKey = this.aiProvider.equals("groq") ? groqApiKey : hfApiToken;
        return selectedKey == null ? "" : selectedKey.trim();
    }

    public boolean isConfigured() {
        return !apiToken.isBlank();
    }"""

new_constructor = """    public HuggingFaceInferenceClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.provider:huggingface}") String aiProvider,
            @Value("${app.ai.hf.api-token:}") String hfApiToken,
            @Value("${app.ai.hf.model-id:Qwen/Qwen2.5-7B-Instruct}") String hfModelId,
            @Value("${app.ai.hf.provider:}") String hfProvider,
            @Value("${app.ai.groq.api-keys:${app.ai.groq.api-key:}}") String groqApiKeys,
            @Value("${app.ai.groq.model-id:openai/gpt-oss-20b}") String groqModelId,
            @Value("${app.ai.finding-enrichment.timeout-ms:15000}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.aiProvider = aiProvider == null || aiProvider.isBlank() ? "huggingface" : aiProvider.trim().toLowerCase();
        this.hfProvider = hfProvider == null ? "" : hfProvider.trim();
        if (!this.aiProvider.equals("huggingface") && !this.aiProvider.equals("groq")) {
            throw new IllegalArgumentException("Unsupported AI_PROVIDER '" + this.aiProvider + "'. Supported values: huggingface, groq.");
        }
        
        String selectedKeysStr = this.aiProvider.equals("groq") ? groqApiKeys : hfApiToken;
        if (selectedKeysStr == null) {
            selectedKeysStr = "";
        }
        
        this.apiKeys = Arrays.stream(selectedKeysStr.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .map(ApiKeyNode::new)
                .collect(Collectors.toList());

        String configuredModelId = this.aiProvider.equals("groq") ? groqModelId : hfModelId;
        String defaultModelId = this.aiProvider.equals("groq") ? "openai/gpt-oss-20b" : "Qwen/Qwen2.5-7B-Instruct";
        this.modelId = configuredModelId == null || configuredModelId.isBlank() ? defaultModelId : configuredModelId.trim();
        this.chatCompletionsUri = this.aiProvider.equals("groq") ? GROQ_CHAT_COMPLETIONS_URI : HUGGING_FACE_CHAT_COMPLETIONS_URI;
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
    }"""
content = content.replace(old_constructor, new_constructor)


# Replace sendChatCompletionRequest
old_send = """    private String sendChatCompletionRequest(FindingAiPrompt findingPrompt) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", getResolvedModelId());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", userPrompt(findingPrompt))
        ));
        payload.put("temperature", 0.2);
        payload.put(aiProvider.equals("groq") ? "max_completion_tokens" : "max_tokens", 800);
        payload.put("stream", false);
        payload.put("response_format", responseFormat());
        if (aiProvider.equals("groq")) {
            payload.put("include_reasoning", false);
        }

        HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            String serviceMessage = extractServiceError(response.body());
            logger.warn("{} enrichment request failed with status {}: {}", aiProvider, response.statusCode(), serviceMessage);
            if (response.statusCode() == 429) {
                throw new RateLimitException(aiProvider + " request failed with status 429"
                        + (serviceMessage.isBlank() ? "." : ": " + serviceMessage),
                        retryDelayMillis(response, serviceMessage));
            }
            throw new IllegalStateException(aiProvider + " request failed with status " + response.statusCode()
                    + (serviceMessage.isBlank() ? "." : ": " + serviceMessage));
        }

        return response.body();
    }"""

new_send = """    private String sendChatCompletionRequest(FindingAiPrompt findingPrompt) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", getResolvedModelId());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", userPrompt(findingPrompt))
        ));
        payload.put("temperature", 0.2);
        payload.put(aiProvider.equals("groq") ? "max_completion_tokens" : "max_tokens", 800);
        payload.put("stream", false);
        payload.put("response_format", responseFormat());
        if (aiProvider.equals("groq")) {
            payload.put("include_reasoning", false);
        }

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
    }"""

content = content.replace(old_send, new_send)

with open("backend/src/main/java/com/scanner/app/service/HuggingFaceInferenceClient.java", "w") as f:
    f.write(content)
