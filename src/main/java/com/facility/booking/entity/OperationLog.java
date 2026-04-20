package com.facility.booking.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "operation_log")
public class OperationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 50)
    private String operatorName;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
