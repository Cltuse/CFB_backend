package com.facility.booking.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "rule_config")
public class RuleConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id")
    private Long categoryId; // NULL表示全局默认规则

    @Column(name = "min_duration_minutes", nullable = false)
    private Integer minDurationMinutes = 30;

    @Column(name = "max_duration_minutes", nullable = false)
    private Integer maxDurationMinutes = 120;

    @Column(name = "advance_days_max", nullable = false)
    private Integer advanceDaysMax = 7;

    @Column(name = "advance_cutoff_minutes", nullable = false)
    private Integer advanceCutoffMinutes = 60;

    @Column(name = "allow_same_day_booking", nullable = false)
    private Boolean allowSameDayBooking = true;

    @Column(name = "max_bookings_per_day", nullable = false)
    private Integer maxBookingsPerDay = 2;

    @Column(name = "max_active_bookings", nullable = false)
    private Integer maxActiveBookings = 3;

    @Column(name = "cancel_deadline_minutes", nullable = false)
    private Integer cancelDeadlineMinutes = 30;

    @Column(name = "need_approval", nullable = false)
    private Boolean needApproval = false;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true; // 是否当前生效

    @Column(name = "time_slot_minutes", nullable = false)
    private Integer timeSlotMinutes = 30;

    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updatedAt;

    @Transient
    private String categoryName;

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