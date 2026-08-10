package com.GroceryEcom.GorceryEcom.infrastructure.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.List;

/**
 * JWT Authentication Details
 * Holds additional information from JWT token
 */
@Data
@AllArgsConstructor
public class JwtAuthenticationDetails {
    private Long userId;
    private String username;
    private List<String> roles;
}

