package com.scanner.app.service;

import com.scanner.app.domain.Finding;
import com.scanner.app.domain.Scan;
import com.scanner.app.domain.Target;
import com.scanner.app.repository.FindingRepository;
import com.scanner.app.websocket.EventPublisher;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FindingEnrichmentServiceRetryClassificationTest {

    @Mock
    private FindingRepository findingRepository;

    @Mock
    private AiInferenceClient aiInferenceClient;

    @Mock
    private EventPublisher eventPublisher;

    @Test
    void shouldNotRetryNonAiFailures() {
        Executor executor = Runnable::run;
        FindingEnrichmentService service = new FindingEnrichmentService(
                findingRepository,
                aiInferenceClient,
                eventPublisher,
                executor,
                true,
                6000,
                1,
                120000,
                300000,
                900000
        );

        Target target = new Target();
        target.setId(10L);
        target.setName("test-target");
        target.setBaseUrl("https://example.com");

        Scan scan = new Scan();
        scan.setId(44L);
        scan.setName("scan-44");

        Finding finding = new Finding();
        finding.setId(331L);
        finding.setScan(scan);
        finding.setTarget(target);
        finding.setToolName("nuclei");
        finding.setCategory("vuln");
        finding.setTitle("Test issue");
        finding.setSeverity("HIGH");
        finding.setAffectedUrl("https://example.com/path");
        finding.setDescription("desc");
        finding.setEvidenceData("evidence");
        finding.setCweId("CWE-79");
        finding.setOwaspCategory("A03");

        when(findingRepository.findWithContextById(331L)).thenReturn(Optional.of(finding));
        when(findingRepository.findDuplicateCandidates(anyLong(), anyLong(), any(Pageable.class))).thenReturn(List.of());
        when(findingRepository.save(any(Finding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiInferenceClient.isConfigured()).thenReturn(true);
        when(aiInferenceClient.getResolvedModelId()).thenReturn("openai/gpt-oss-20b");
        when(aiInferenceClient.enrichFinding(any())).thenThrow(new LazyInitializationException("Could not initialize proxy [Target#10] - no session"));

        assertDoesNotThrow(() -> service.enrichFindingNow(331L));

        verify(aiInferenceClient, times(1)).enrichFinding(any());
        verify(findingRepository, atLeastOnce()).save(any(Finding.class));
    }
}
