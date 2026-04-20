package com.facility.booking.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_recommendation")
public class UserRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long facilityId;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal score;

    @Column(length = 100)
    private String reason;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime generatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.generatedAt = LocalDateTime.now();
    }
}