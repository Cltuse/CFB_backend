package com.facility.booking.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long expiresIn;
    private UserProfile user;

    @Data
    @AllArgsConstructor
    public static class UserProfile {
        private Long id;
        private String username;
        private String realName;
        private String role;
        private String phone;
        private String email;
        private String avatar;
        private String status;
    }
}
