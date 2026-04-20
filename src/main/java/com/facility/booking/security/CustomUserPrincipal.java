package com.facility.booking.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

public record CustomUserPrincipal(
        Long id,
        String username,
        String realName,
        String role,
        String status
) {
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
