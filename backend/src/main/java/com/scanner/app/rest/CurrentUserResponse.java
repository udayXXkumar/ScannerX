package com.scanner.app.rest;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CurrentUserResponse {
    private Long id;
    private String email;
    private String role;
    private String status;
    private String fullName;
}
