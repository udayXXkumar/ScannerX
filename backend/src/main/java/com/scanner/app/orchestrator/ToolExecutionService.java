package com.scanner.app.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class ToolExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionService.class);
    private static final Map<String, List<String>> EXECUTABLE_ALIASES = Map.of(
            "httpx", List.of("httpx-toolkit")
    );

    private final Map<String, Optional<String>> executableCache = new ConcurrentHashMap<>();
    private final Map<Long, Set<Process>> activeProcesses = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile Boolean dockerAvailable;

    public String normalizeTargetUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return rawUrl;
        }

        try {
            URI original = URI.create(rawUrl);
            String path = original.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }

            URI sanitized = new URI(
                    original.getScheme(),
                    original.getUserInfo(),
                    original.getHost(),
                    original.getPort(),
                    path,
                    original.getQuery(),
                    null
            );
            return sanitized.toString();
        } catch (Exception ignored) {
            int hashIndex = rawUrl.indexOf('#');
            return hashIndex >= 0 ? rawUrl.substring(0, hashIndex) : rawUrl;
        }
    }

    public String buildFuzzUrl(String rawUrl) {
        String targetUrl = normalizeTargetUrl(rawUrl);
        if (targetUrl == null || targetUrl.isBlank()) {
            return targetUrl;
        }
        return targetUrl.endsWith("/") ? targetUrl + "FUZZ" : targetUrl + "/FUZZ";
    }

    public long detectBaselineLength(String rawUrl) {
        String targetUrl = normalizeTargetUrl(rawUrl);
        if (targetUrl == null || targetUrl.isBlank()) {
            return -1;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .uri(URI.create(targetUrl))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body() == null ? -1 : response.body().length();
        } catch (Exception e) {
            log.debug("Unable to detect baseline length for {}", targetUrl, e);
            return -1;
        }
    }

    public String resolveWordlist(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }

            if (Files.isReadable(Path.of(candidate))) {
                return candidate;
            }
        }

        throw new IllegalStateException("No readable wordlist found on this host.");
    }

    public boolean isExecutableAvailable(String candidate) {
        return resolveExecutable(candidate) != null;
    }

    public Process startManagedProcess(Long scanId, ProcessBuilder processBuilder) throws IOException {
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        registerActiveProcess(scanId, process);
        return process;
    }

    public void registerActiveProcess(Long scanId, Process process) {
        if (scanId == null || process == null) {
            return;
        }

        activeProcesses.computeIfAbsent(scanId, ignored -> ConcurrentHashMap.newKeySet()).add(process);
    }

    public void unregisterActiveProcess(Long scanId, Process process) {
        if (scanId == null || process == null) {
            return;
        }

        Set<Process> processes = activeProcesses.get(scanId);
        if (processes == null) {
            return;
        }

        processes.remove(process);
        if (processes.isEmpty()) {
            activeProcesses.remove(scanId, processes);
        }
    }

    public Process startProcess(List<String> nativeCommand, List<String> dockerCommand) throws IOException {
        if (nativeCommand != null && !nativeCommand.isEmpty()) {
            String resolvedExecutable = resolveExecutable(nativeCommand.get(0));
            if (resolvedExecutable != null) {
                List<String> resolvedCommand = new ArrayList<>(nativeCommand);
                resolvedCommand.set(0, resolvedExecutable);
                return new ProcessBuilder(resolvedCommand)
                        .redirectErrorStream(true)
                        .start();
            }
        }

        if (dockerCommand != null && !dockerCommand.isEmpty() && canUseDocker()) {
            return new ProcessBuilder(dockerCommand)
                    .redirectErrorStream(true)
                    .start();
        }

        String toolName = nativeCommand != null && !nativeCommand.isEmpty()
                ? nativeCommand.get(0)
                : dockerCommand != null && dockerCommand.size() > 2
                ? dockerCommand.get(2)
                : "scanner tool";

        throw new IllegalStateException(toolName + " is unavailable locally and Docker cannot be used.");
    }

    public StreamingProcessResult runStreamingProcess(
            Long scanId,
            List<String> nativeCommand,
            List<String> dockerCommand,
            Duration timeout,
            Consumer<String> lineConsumer
    ) throws IOException, InterruptedException {
        return streamProcess(scanId, startProcess(nativeCommand, dockerCommand), timeout, lineConsumer);
    }

    public StreamingProcessResult runStreamingProcess(
            List<String> nativeCommand,
            List<String> dockerCommand,
            Duration timeout,
            Consumer<String> lineConsumer
    ) throws IOException, InterruptedException {
        return streamProcess(null, startProcess(nativeCommand, dockerCommand), timeout, lineConsumer);
    }

    public StreamingProcessResult runStreamingProcess(
            Long scanId,
            ProcessBuilder nativeBuilder,
            ProcessBuilder dockerBuilder,
            Duration timeout,
            Consumer<String> lineConsumer
    ) throws IOException, InterruptedException {
        return streamProcess(scanId, startProcess(nativeBuilder, dockerBuilder), timeout, lineConsumer);
    }

    public StreamingProcessResult runStreamingProcess(
            ProcessBuilder processBuilder,
            Duration timeout,
            Consumer<String> lineConsumer
    ) throws IOException, InterruptedException {
        processBuilder.redirectErrorStream(true);
        return streamProcess(null, processBuilder.start(), timeout, lineConsumer);
    }

    public void stopActiveProcesses(Long scanId) {
        if (scanId == null) {
            return;
        }

        Set<Process> processes = activeProcesses.remove(scanId);
        if (processes != null) {
            processes.forEach(this::destroyProcessTree);
        }
    }

    public String stripAnsi(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    public boolean hasQueryParameters(String rawUrl) {
        try {
            URI uri = URI.create(normalizeTargetUrl(rawUrl));
            return uri.getQuery() != null && !uri.getQuery().isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String resolveExecutable(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        return executableCache.computeIfAbsent(candidate, this::findExecutable)
                .orElse(null);
    }

    private Optional<String> findExecutable(String candidate) {
        Optional<String> resolved = findExecutableByName(candidate);
        if (resolved.isPresent()) {
            return resolved;
        }

        for (String alias : EXECUTABLE_ALIASES.getOrDefault(candidate, List.of())) {
            resolved = findExecutableByName(alias);
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        return Optional.empty();
    }

    private Optional<String> findExecutableByName(String candidate) {
        Path directPath = Path.of(candidate);
        if (directPath.isAbsolute() || candidate.contains("/")) {
            return Files.isExecutable(directPath) ? Optional.of(directPath.toString()) : Optional.empty();
        }

        LinkedHashSet<Path> searchDirectories = new LinkedHashSet<>();

        String pathValue = System.getenv("PATH");
        if (pathValue != null && !pathValue.isBlank()) {
            for (String pathEntry : pathValue.split(":")) {
                if (pathEntry == null || pathEntry.isBlank()) {
                    continue;
                }
                searchDirectories.add(Path.of(pathEntry));
            }
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            searchDirectories.add(Path.of(userHome, "go", "bin"));
            searchDirectories.add(Path.of(userHome, ".local", "bin"));
            searchDirectories.add(Path.of(userHome, "bin"));
        }

        String goBin = System.getenv("GOBIN");
        if (goBin != null && !goBin.isBlank()) {
            searchDirectories.add(Path.of(goBin));
        }

        String goPath = System.getenv("GOPATH");
        if (goPath != null && !goPath.isBlank()) {
            searchDirectories.add(Path.of(goPath, "bin"));
        }

        for (Path searchDirectory : searchDirectories) {
            Path executable = searchDirectory.resolve(candidate);
            if (Files.isExecutable(executable)) {
                return Optional.of(executable.toString());
            }
        }

        return Optional.empty();
    }

    private boolean canUseDocker() {
        if (dockerAvailable != null) {
            return dockerAvailable;
        }

        synchronized (this) {
            if (dockerAvailable != null) {
                return dockerAvailable;
            }

            try {
                Process process = new ProcessBuilder("docker", "info")
                        .redirectErrorStream(true)
                        .start();
                int exitCode = process.waitFor();
                dockerAvailable = exitCode == 0;
            } catch (Exception e) {
                dockerAvailable = false;
            }

            return dockerAvailable;
        }
    }

    private Process startProcess(ProcessBuilder nativeBuilder, ProcessBuilder dockerBuilder) throws IOException {
        if (nativeBuilder != null && nativeBuilder.command() != null && !nativeBuilder.command().isEmpty()) {
            String resolvedExecutable = resolveExecutable(nativeBuilder.command().getFirst());
            if (resolvedExecutable != null) {
                nativeBuilder.command().set(0, resolvedExecutable);
                nativeBuilder.redirectErrorStream(true);
                return nativeBuilder.start();
            }
        }

        if (dockerBuilder != null && dockerBuilder.command() != null && !dockerBuilder.command().isEmpty() && canUseDocker()) {
            dockerBuilder.redirectErrorStream(true);
            return dockerBuilder.start();
        }

        String toolName = nativeBuilder != null && nativeBuilder.command() != null && !nativeBuilder.command().isEmpty()
                ? nativeBuilder.command().getFirst()
                : dockerBuilder != null && dockerBuilder.command() != null && dockerBuilder.command().size() > 2
                ? dockerBuilder.command().get(2)
                : "scanner tool";

        throw new IllegalStateException(toolName + " is unavailable locally and Docker cannot be used.");
    }

    private StreamingProcessResult streamProcess(Long scanId, Process process, Duration timeout, Consumer<String> lineConsumer) throws InterruptedException {
        registerActiveProcess(scanId, process);

        var executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tool-process-stream");
            thread.setDaemon(true);
            return thread;
        });

        Future<?> readerFuture = executor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        lineConsumer.accept(line);
                    } catch (Exception callbackException) {
                        log.debug("Tool output callback failed", callbackException);
                    }
                }
            } catch (IOException ioException) {
                log.debug("Tool output stream closed", ioException);
            }
        });

        boolean finished = false;
        boolean timedOut = false;
        try {
            if (timeout == null) {
                process.waitFor();
                finished = true;
            } else {
                finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                timedOut = !finished;
            }
        } catch (InterruptedException interruptedException) {
            destroyProcessTree(process);
            throw interruptedException;
        } finally {
            if (!finished && process.isAlive()) {
                destroyProcessTree(process);
            }

            try {
                readerFuture.get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                readerFuture.cancel(true);
            } finally {
                executor.shutdownNow();
                unregisterActiveProcess(scanId, process);
            }
        }

        return new StreamingProcessResult(finished ? process.exitValue() : -1, timedOut);
    }

    public void destroyProcessTree(Process process) {
        try {
            process.toHandle().descendants().forEach(handle -> {
                try {
                    handle.destroyForcibly();
                } catch (Exception ignored) {
                    // Best effort process cleanup.
                }
            });
        } catch (Exception ignored) {
            // Best effort process cleanup.
        }

        try {
            process.destroyForcibly();
        } catch (Exception ignored) {
            // Best effort process cleanup.
        }
    }

    public record StreamingProcessResult(int exitCode, boolean timedOut) {}
}
