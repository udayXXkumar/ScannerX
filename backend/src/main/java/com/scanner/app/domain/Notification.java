package com.scanner.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "scan_id")
    private Long scanId;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "finding_count")
    private Integer findingCount;

    @Column(name = "is_read")
    private boolean read;
    private LocalDateTime createdAt;
    private String type;
}
