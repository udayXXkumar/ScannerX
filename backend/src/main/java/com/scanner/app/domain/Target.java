package com.scanner.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "targets")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
public class Target {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;
    
    private String name;
    private String baseUrl;
    private String domain;
    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    private String verificationStatus;
    private String verificationToken;
    private String defaultTier;
    private Boolean timeoutsEnabled;
    
    private String projectGroupName;
    private String tags;
    
    private LocalDateTime lastScanAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
