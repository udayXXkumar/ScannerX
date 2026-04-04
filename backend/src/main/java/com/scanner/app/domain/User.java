package com.scanner.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String fullName;
    private String email;

    @JsonIgnore
    private String passwordHash;

    private String role;

    @Column(name = "status")
    private String status;
    
    @Column(name = "reset_token")
    @JsonIgnore
    private String resetToken;
    
    @Column(name = "reset_token_expiry")
    @JsonIgnore
    private LocalDateTime resetTokenExpiry;
    
    @Column(name = "email_verification_token")
    @JsonIgnore
    private String emailVerificationToken;
    
    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
}
