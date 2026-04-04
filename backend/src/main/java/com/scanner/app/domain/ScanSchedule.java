package com.scanner.app.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scan_schedules")
public class ScanSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private Target target;

    private String cronExpression;
    private String scanProfile; // e.g. "FULL", "QUICK"
    private String status; // e.g. "ACTIVE", "PAUSED"
    
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
    
    public ScanSchedule() {}

    public Long getId() { return id; }
    public Target getTarget() { return target; }
    public void setTarget(Target target) { this.target = target; }
    
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    
    public String getScanProfile() { return scanProfile; }
    public void setScanProfile(String scanProfile) { this.scanProfile = scanProfile; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
}
