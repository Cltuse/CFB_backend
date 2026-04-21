package com.facility.booking.entity;

import com.facility.booking.util.StoredFileUrlUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "facility")
public class Facility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String model;

    @Column(length = 50)
    private String category;

    @Column(length = 200)
    private String location;

    @Column(name = "maintainer_id")
    private Long maintainerId;

    @Column(length = 20)
    private String status = "AVAILABLE"; // AVAILABLE, MAINTENANCE, DAMAGED

    @Column(columnDefinition = "TEXT")
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Shanghai")
  private LocalDate purchaseDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 500)
    private String imageUrl = "/files/facility/default-facility.svg";

    @Transient
    private String maintainerName;

    public String getImageUrl() {
        return StoredFileUrlUtils.normalizeForClient(imageUrl);
    }

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
