package com.facility.booking.entity;

import com.facility.booking.util.StoredFileUrlUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String realName;

    // 提供getName()方法以兼容现有代码
    public String getName() {
        return this.realName;
    }

    @Column(nullable = false, length = 20)
    private String role; // USER, ADMIN, TEACHER, STUDENT

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 500)
    private String avatar;

    public String getAvatar() {
        return StoredFileUrlUtils.normalizeForClient(avatar);
    }

    @Column(length = 20)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    @Column(name = "credit_score")
    private Integer creditScore = 100;

    @Column(name = "violation_count")
    private Integer violationCount = 0;

    @Column(updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
