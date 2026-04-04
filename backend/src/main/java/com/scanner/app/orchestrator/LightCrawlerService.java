package com.scanner.app.orchestrator;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LightCrawlerService {

    private static final Pattern LINK_PATTERN = Pattern.compile("(?i)(?:href|src)=[\"']([^\"'#]+)[\"']");
    private static final Pattern FORM_PATTERN = Pattern.compile("(?i)<form[^>]+action=[\"']([^\"']*)[\"']");
    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("[?&]([^=&]+)=");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public StepExecutionResult crawl(String normalizedTargetUrl, int maxDepth, int maxUrls) {
        Set<String> discoveredUrls = new LinkedHashSet<>();
        Set<String> forms = new LinkedHashSet<>();
        Set<String> queryParameters = new LinkedHashSet<>();
        Set<String> jsonEndpoints = new LinkedHashSet<>();

        URI root = URI.create(normalizedTargetUrl);
        Queue<CrawlEntry> queue = new ArrayDeque<>();
        queue.add(new CrawlEntry(root.toString(), 0));
        discoveredUrls.add(root.toString());

        while (!queue.isEmpty() && discoveredUrls.size() < maxUrls) {
            CrawlEntry current = queue.poll();
            if (current.depth() > maxDepth) {
                continue;
            }

            try {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder()
                                .GET()
                                .timeout(Duration.ofSeconds(10))
                                .uri(URI.create(current.url()))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
                String body = response.body() == null ? "" : response.body();

                if (contentType.contains("json") || current.url().endsWith(".json")) {
                    jsonEndpoints.add(current.url());
                }

                queryParameters.addAll(extractQueryParameters(current.url()));

                if (!contentType.contains("html") && !looksLikeHtml(body)) {
                    continue;
                }

                for (String formAction : extractMatches(body, FORM_PATTERN)) {
                    String resolved = resolve(root, current.url(), formAction);
                    if (resolved != null) {
                        forms.add(resolved);
                        discoveredUrls.add(resolved);
                    }
                }

                for (String candidate : extractMatches(body, LINK_PATTERN)) {
                    String resolved = resolve(root, current.url(), candidate);
                    if (resolved == null || !sameHost(root, resolved) || discoveredUrls.contains(resolved)) {
                        continue;
                    }

                    discoveredUrls.add(resolved);
                    queryParameters.addAll(extractQueryParameters(resolved));
                    if (resolved.endsWith(".json") || resolved.contains("/api/")) {
                        jsonEndpoints.add(resolved);
                    }
                    queue.add(new CrawlEntry(resolved, current.depth() + 1));
                    if (discoveredUrls.size() >= maxUrls) {
                        break;
                    }
                }
            } catch (Exception ignored) {
                // Best-effort crawl only.
            }
        }

        return StepExecutionResult.success(
                "Discovery scope updated.",
                discoveredUrls,
                forms,
                queryParameters,
                jsonEndpoints
        );
    }

    private Set<String> extractMatches(String body, Pattern pattern) {
        Set<String> matches = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value != null && !value.isBlank()) {
                matches.add(value.trim());
            }
        }
        return matches;
    }

    private Set<String> extractQueryParameters(String url) {
        Set<String> params = new LinkedHashSet<>();
        Matcher matcher = QUERY_PARAM_PATTERN.matcher(url);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }

    private String resolve(URI root, String currentUrl, String relativeOrAbsolute) {
        if (relativeOrAbsolute == null || relativeOrAbsolute.isBlank()) {
            return null;
        }

        try {
            URI base = URI.create(currentUrl);
            URI resolved = base.resolve(relativeOrAbsolute);
            URI sanitized = new URI(
                    resolved.getScheme(),
                    resolved.getUserInfo(),
                    resolved.getHost(),
                    resolved.getPort(),
                    resolved.getPath() == null || resolved.getPath().isBlank() ? "/" : resolved.getPath(),
                    resolved.getQuery(),
                    null
            );
            return sameHost(root, sanitized.toString()) ? sanitized.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean sameHost(URI root, String candidateUrl) {
        try {
            URI candidate = URI.create(candidateUrl);
            return root.getHost() != null && root.getHost().equalsIgnoreCase(candidate.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean looksLikeHtml(String body) {
        String snippet = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return snippet.contains("<html") || snippet.contains("<body") || snippet.contains("<a ");
    }

    private record CrawlEntry(String url, int depth) {}
}
