package com.scanner.app.service;

import com.scanner.app.domain.Finding;
import com.scanner.app.repository.FindingRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FindingService {

    private final FindingRepository findingRepository;
    private final FindingSeverityService findingSeverityService;
    private final FindingEnrichmentService findingEnrichmentService;

    public FindingService(
            FindingRepository findingRepository,
            FindingSeverityService findingSeverityService,
            FindingEnrichmentService findingEnrichmentService
    ) {
        this.findingRepository = findingRepository;
        this.findingSeverityService = findingSeverityService;
        this.findingEnrichmentService = findingEnrichmentService;
    }

    public Finding saveOrUpdateFinding(Finding newFinding) {
        newFinding.setSeverity(
                findingSeverityService.resolveSeverity(
                        newFinding.getSeverity(),
                        newFinding.getTitle(),
                        newFinding.getDescription(),
                        newFinding.getCategory(),
                        newFinding.getAffectedUrl()
                )
        );

        List<Finding> existingFindings = findingRepository.findExistingFindings(
                newFinding.getTarget().getId(),
                newFinding.getTitle(),
                newFinding.getAffectedUrl()
        );

        Finding sameScanFinding = existingFindings.stream()
                .filter(existing -> existing.getScan() != null
                        && newFinding.getScan() != null
                        && existing.getScan().getId().equals(newFinding.getScan().getId()))
                .findFirst()
                .orElse(null);

        if (sameScanFinding != null) {
            sameScanFinding.setLastSeenAt(LocalDateTime.now());
            sameScanFinding.setSeverity(newFinding.getSeverity());
            sameScanFinding.setDescription(newFinding.getDescription());
            sameScanFinding.setEvidenceData(newFinding.getEvidenceData());
            sameScanFinding.setToolName(newFinding.getToolName());
            sameScanFinding.setCategory(newFinding.getCategory());
            sameScanFinding.setStatus(newFinding.getStatus());
            sameScanFinding.setRemediation(newFinding.getRemediation());
            sameScanFinding.setCweId(newFinding.getCweId());
            sameScanFinding.setOwaspCategory(newFinding.getOwaspCategory());
            Finding savedFinding = findingRepository.save(sameScanFinding);
            findingEnrichmentService.requestEnrichment(savedFinding);
            return savedFinding;
        }

        if (!existingFindings.isEmpty()) {
            Finding existing = existingFindings.get(0);
            existing.setLastSeenAt(LocalDateTime.now());

            newFinding.setFirstSeenAt(existing.getFirstSeenAt() != null ? existing.getFirstSeenAt() : LocalDateTime.now());
            newFinding.setLastSeenAt(LocalDateTime.now());

            if ("RESOLVED".equals(existing.getStatus()) || "FALSE_POSITIVE".equals(existing.getStatus())) {
                newFinding.setStatus("REOPENED");
            }
        }

        if (newFinding.getFirstSeenAt() == null) {
            newFinding.setFirstSeenAt(LocalDateTime.now());
        }
        newFinding.setLastSeenAt(LocalDateTime.now());
        Finding savedFinding = findingRepository.save(newFinding);
        findingEnrichmentService.requestEnrichment(savedFinding);
        return savedFinding;
    }
}
