package com.scanner.app.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be a valid address.")
    private String email;
}
