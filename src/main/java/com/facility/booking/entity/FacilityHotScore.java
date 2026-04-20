package com.facility.booking.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "facility_hot_score")
public class FacilityHotScore {
    @Id
    @Column(name = "facility_id")
    private Long facilityId;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal hotScore;

    @Column(nullable = false)
    private Integer totalBookings;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}