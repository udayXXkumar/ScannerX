package com.scanner.app.rest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @NotBlank(message = "Full name is required.")
    private String fullName;
}
