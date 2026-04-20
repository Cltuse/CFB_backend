package com.facility.booking.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public CustomUserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    public Long getCurrentUserId() {
        CustomUserPrincipal principal = getCurrentUser();
        return principal == null ? null : principal.id();
    }

    public String getCurrentUserName() {
        CustomUserPrincipal principal = getCurrentUser();
        if (principal == null) {
            return null;
        }
        if (principal.realName() != null && !principal.realName().isBlank()) {
            return principal.realName();
        }
        return principal.username();
    }

    public boolean hasRole(String role) {
        CustomUserPrincipal principal = getCurrentUser();
        return principal != null && role != null && role.equals(principal.role());
    }
}
