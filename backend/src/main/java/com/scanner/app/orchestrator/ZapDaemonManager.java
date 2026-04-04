package com.scanner.app.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ZapDaemonManager {

    private static final Logger log = LoggerFactory.getLogger(ZapDaemonManager.class);
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofSeconds(60);

    private final ToolExecutionService toolExecutionService;
    private final ZapApiClient zapApiClient;
    private final Map<Long, ZapDaemonSession> activeSessions = new ConcurrentHashMap<>();

    public ZapDaemonManager(ToolExecutionService toolExecutionService, ZapApiClient zapApiClient) {
        this.toolExecutionService = toolExecutionService;
        this.zapApiClient = zapApiClient;
    }

    public ZapDaemonSession ensureSession(Long scanId, ScanExecutionContext context) throws Exception {
        ZapDaemonSession existingSession = activeSessions.get(scanId);
        if (existingSession != null && existingSession.process().isAlive()) {
            return existingSession;
        }

        if (existingSession != null) {
            shutdown(scanId, context);
        }

        ZapDaemonSession session = start(scanId, DEFAULT_STARTUP_TIMEOUT);
        activeSessions.put(scanId, session);
        context.setZapPort(session.port());
        context.setZapApiKey(session.apiKey());
        context.setZapBaseUrl(session.baseUrl());
        context.setZapWorkingDirectory(session.workingDirectory().toString());
        return session;
    }

    public ZapCapabilities probeCapabilities(Duration startupTimeout) {
        ZapDaemonSession session = null;
        try {
            session = start(null, startupTimeout == null ? DEFAULT_STARTUP_TIMEOUT : startupTimeout);
            return new ZapCapabilities(
                    zapApiClient.supportsSpider(session),
                    zapApiClient.supportsAjaxSpider(session),
                    zapApiClient.supportsPassiveScan(session),
                    zapApiClient.supportsActiveScan(session)
            );
        } catch (Exception exception) {
            log.warn("Unable to verify local ZAP daemon capabilities", exception);
            return new ZapCapabilities(false, false, false, false);
        } finally {
            shutdown(session);
        }
    }

    public void shutdown(Long scanId, ScanExecutionContext context) {
        ZapDaemonSession session = scanId == null ? null : activeSessions.remove(scanId);
        if (session == null && context != null && context.getZapPort() != null && context.getZapApiKey() != null) {
            session = new ZapDaemonSession(
                    scanId,
                    context.getZapPort(),
                    context.getZapApiKey(),
                    context.getZapBaseUrl(),
                    context.getZapWorkingDirectory() == null ? null : Path.of(context.getZapWorkingDirectory()),
                    null
            );
        }

        shutdown(session);
        clearContext(context);
    }

    public void clearContext(ScanExecutionContext context) {
        if (context == null) {
            return;
        }

        context.setZapPort(null);
        context.setZapApiKey(null);
        context.setZapBaseUrl(null);
        context.setZapWorkingDirectory(null);
    }

    private ZapDaemonSession start(Long scanId, Duration startupTimeout) throws Exception {
        Path workingDirectory = Files.createTempDirectory("scannerx-zap-");
        int port = reservePort();
        String apiKey = UUID.randomUUID().toString().replace("-", "");

        ProcessBuilder processBuilder = new ProcessBuilder(List.of(
                "zaproxy",
                "-daemon",
                "-dir", workingDirectory.toString(),
                "-host", "127.0.0.1",
                "-port", String.valueOf(port),
                "-config", "api.disablekey=false",
                "-config", "api.key=" + apiKey,
                "-config", "api.addrs.addr.name=.*",
                "-config", "api.addrs.addr.regex=true",
                "-silent",
                "-notel",
                "-nostdout"
        ));
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process = toolExecutionService.startManagedProcess(scanId, processBuilder);
        ZapDaemonSession session = new ZapDaemonSession(
                scanId,
                port,
                apiKey,
                "http://127.0.0.1:" + port,
                workingDirectory,
                process
        );

        if (!zapApiClient.waitUntilReady(session, startupTimeout)) {
            shutdown(session);
            throw new IllegalStateException("Local ZAP daemon did not become ready in time.");
        }

        return session;
    }

    private void shutdown(ZapDaemonSession session) {
        if (session == null) {
            return;
        }

        try {
            zapApiClient.shutdown(session);
        } catch (Exception ignored) {
            // Best-effort shutdown.
        }

        if (session.process() != null) {
            toolExecutionService.destroyProcessTree(session.process());
            toolExecutionService.unregisterActiveProcess(session.scanId(), session.process());
        }

        if (session.workingDirectory() != null) {
            cleanupDirectory(session.workingDirectory());
        }
    }

    private int reservePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setReuseAddress(true);
            return serverSocket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to allocate a local port for ZAP.", exception);
        }
    }

    private void cleanupDirectory(Path workingDirectory) {
        try {
            Files.walk(workingDirectory)
                    .sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best-effort cleanup only.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    public record ZapDaemonSession(
            Long scanId,
            int port,
            String apiKey,
            String baseUrl,
            Path workingDirectory,
            Process process
    ) {
    }

    public record ZapCapabilities(
            boolean spiderAvailable,
            boolean ajaxSpiderAvailable,
            boolean passiveScanAvailable,
            boolean activeScanAvailable
    ) {
    }
}
