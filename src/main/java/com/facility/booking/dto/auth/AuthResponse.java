package com.facility.booking.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

// 认证响应DTO
@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long expiresIn;
    private UserProfile user;

    // 用户信息DTO
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
