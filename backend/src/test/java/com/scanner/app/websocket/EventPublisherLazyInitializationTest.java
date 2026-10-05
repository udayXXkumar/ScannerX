package com.scanner.app.websocket;

import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Scan;
import com.scanner.app.domain.Target;
import com.scanner.app.service.ScanActivityService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
class EventPublisherLazyInitializationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void shouldSerializeDetachedFindingWithoutLazyInitializationException() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ScanActivityService scanActivityService = mock(ScanActivityService.class);
        EventPublisher eventPublisher = new EventPublisher(messagingTemplate, scanActivityService);

        Target target = new Target();
        target.setName("target-name");
        target.setBaseUrl("https://example.test");
        target.setDomain("example.test");
        entityManager.persist(target);

        Scan scan = new Scan();
        scan.setName("scan-name");
        scan.setTarget(target);
        entityManager.persist(scan);

        Finding finding = new Finding();
        finding.setScan(scan);
        finding.setTarget(target);
        finding.setToolName("nuclei");
        finding.setCategory("vuln");
        finding.setTitle("Title");
        finding.setSeverity("HIGH");
        finding.setAffectedUrl("https://example.test/path");
        finding.setDescription("desc");
        entityManager.persist(finding);
        entityManager.flush();
        entityManager.clear();

        Finding detachedFinding = entityManager.find(Finding.class, finding.getId());
        entityManager.clear();

        assertDoesNotThrow(() -> eventPublisher.publishScanEvent(detachedFinding.getScan().getId(), "FINDING_ENRICHED", detachedFinding));
    }
}
