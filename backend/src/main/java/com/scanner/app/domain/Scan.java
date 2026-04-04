package com.scanner.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "scans")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
public class Scan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id")
    private Target target;

    private String name;
    private String profileType;
    private String tier;
    private String status;
    private Integer progress;
    private Integer riskScore;
    private Boolean timeoutsEnabled;
    private Boolean pauseRequested;
    private Integer currentStageOrder;
    private Integer resumeStageOrder;

    @Column(columnDefinition = "TEXT")
    private String executionContextJson;

    @Column(columnDefinition = "TEXT")
    private String normalizedReportJson;

    private LocalDateTime startedAt;
    private LocalDateTime pausedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
