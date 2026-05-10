package com.facility.booking.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
// 用户登录请求DTO
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
