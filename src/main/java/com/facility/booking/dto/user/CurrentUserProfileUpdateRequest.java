package com.facility.booking.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CurrentUserProfileUpdateRequest {
    @NotBlank(message = "真实姓名不能为空")
    @Size(min = 2, max = 50, message = "真实姓名长度必须在 2 到 50 个字符之间")
    private String realName;

    @Size(max = 20, message = "手机号长度不能超过 20 个字符")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100 个字符")
    private String email;

    @Size(max = 500, message = "头像地址长度不能超过 500 个字符")
    private String avatar;
}
