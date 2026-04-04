package com.scanner.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "scan_activity")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
public class ScanActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id")
    @JsonIgnore
    private Scan scan;

    private String type;
    private Integer stageOrder;

    @Column(columnDefinition = "TEXT")
    private String message;

    private LocalDateTime createdAt;
}
