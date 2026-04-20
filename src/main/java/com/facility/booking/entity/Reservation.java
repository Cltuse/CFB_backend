package com.facility.booking.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reservation")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long facilityId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startTime;

    @Column(nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endTime;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(length = 20)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED, COMPLETED, CANCELLED

    @Column(columnDefinition = "TEXT")
    private String adminRemark;

    @Basic
    @Column(updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    @Basic
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updatedAt;

    @Column(name = "checkin_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime checkinTime;

    @Column(name = "checkout_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime checkoutTime;

    @Column(name = "checkin_status", length = 20)
    private String checkinStatus = "NOT_CHECKED"; // NOT_CHECKED-未签到/CHECKED_IN-已签到/CHECKED_OUT-已签退/MISSED-爽约

    @Column(name = "verification_code", length = 64)
    private String verificationCode;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime verifiedTime;

    @Transient
    private String facilityName;

    @Transient
    private String userName;

    @Transient
    private String userRole;

    @Transient
    private String verifiedByName;

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