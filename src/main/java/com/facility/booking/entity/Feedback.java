package com.facility.booking.entity;

import com.facility.booking.util.StoredFileUrlUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "`feedback`")
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 20)
    private String type = "SUGGESTION"; // SUGGESTION-建议/COMPLAINT-投诉/QUESTION-咨询

    @Column(length = 20)
    private String status = "PENDING"; // PENDING-待处理/PROCESSED-已处理

    @Column(columnDefinition = "TEXT")
    private String reply;

    @Column(name = "`reply_time`")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime replyTime;

    @Column(name = "`reply_by`")
    private Long replyBy;

    @Basic
    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    @Basic
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updatedAt;

    @Transient
    private String userName;

    @Transient
    private String userRole;

    @Transient
    private String userAvatar;

    public String getUserAvatar() {
        return StoredFileUrlUtils.normalizeForClient(userAvatar);
    }

    @Transient
    private String replyByName;

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
