package com.scanner.app.queue;

import com.scanner.app.orchestrator.ScannerOrchestrator;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.queue.mode", havingValue = "rabbit")
public class ScanConsumer {

    private final ScannerOrchestrator scannerOrchestrator;

    public ScanConsumer(ScannerOrchestrator scannerOrchestrator) {
        this.scannerOrchestrator = scannerOrchestrator;
    }

    @RabbitListener(queues = RabbitConfig.SCAN_QUEUE)
    public void receiveScanJob(String message) {
        try {
            Long scanId = Long.parseLong(message);
            scannerOrchestrator.runScan(scanId);
        } catch (Exception e) {
            e.printStackTrace(); 
        }
    }
}
