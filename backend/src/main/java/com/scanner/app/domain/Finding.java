package com.scanner.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "findings")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
public class Finding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id")
    @JsonIgnore
    private Scan scan;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id")
    private Target target;
    
    @JsonIgnore
    private String toolName;
    private String category;
    private String title;
    private String severity;
    private String status;
    
    @Column(columnDefinition = "TEXT")
    private String affectedUrl;
    
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String evidenceData;
    
    @Column(columnDefinition = "TEXT")
    private String remediation;

    @Column(columnDefinition = "TEXT")
    private String comments;

    private String assignedUser;
    private String cweId;
    private String owaspCategory;

    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
}
