package com.scanner.app.service;

import com.scanner.app.domain.Scan;
import com.scanner.app.domain.ScanActivity;
import com.scanner.app.repository.ScanActivityRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScanActivityService {

    private final ScanActivityRepository scanActivityRepository;
    private final EntityManager entityManager;

    public ScanActivityService(ScanActivityRepository scanActivityRepository, EntityManager entityManager) {
        this.scanActivityRepository = scanActivityRepository;
        this.entityManager = entityManager;
    }

    public void record(Long scanId, String type, Integer stageOrder, String message, LocalDateTime createdAt) {
        if (scanId == null || type == null || type.isBlank()) {
            return;
        }

        ScanActivity activity = new ScanActivity();
        activity.setScan(entityManager.getReference(Scan.class, scanId));
        activity.setType(type);
        activity.setStageOrder(stageOrder);
        activity.setMessage(message);
        activity.setCreatedAt(createdAt == null ? LocalDateTime.now() : createdAt);
        scanActivityRepository.save(activity);
    }

    public List<ScanActivity> getActivityForUser(Long scanId, Long userId) {
        return scanActivityRepository.findByScanIdAndScanUserIdOrderByCreatedAtAsc(scanId, userId);
    }
}
