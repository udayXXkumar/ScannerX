package com.scanner.app.queue;

import com.scanner.app.orchestrator.ScannerOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ScanProducer {

    private static final Logger log = LoggerFactory.getLogger(ScanProducer.class);

    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final ScannerOrchestrator scannerOrchestrator;
    private final String queueMode;

    public ScanProducer(
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            ScannerOrchestrator scannerOrchestrator,
            @Value("${app.queue.mode:local}") String queueMode
    ) {
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        this.scannerOrchestrator = scannerOrchestrator;
        this.queueMode = queueMode;
    }

    public void sendScanJob(Long scanId) {
        if ("rabbit".equalsIgnoreCase(queueMode)) {
            RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
            if (rabbitTemplate == null) {
                throw new IllegalStateException("RabbitMQ queue mode is enabled, but RabbitTemplate is unavailable.");
            }
            rabbitTemplate.convertAndSend(RabbitConfig.SCAN_QUEUE, scanId.toString());
            return;
        }

        log.info("Running scan {} in local queue mode", scanId);
        CompletableFuture.runAsync(() -> scannerOrchestrator.runScan(scanId))
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        log.error("Local scan {} crashed before it could update its final status", scanId, throwable);
                    }
                });
    }
}
