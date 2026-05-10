package com.facility.booking.repository;

import com.facility.booking.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 根据状态查询所有通知
    List<Notice> findByStatus(String status);

    // 查询状态为SCHEDULED的通知数量，且计划发布时间小于等于当前时间戳，返回所有符合条件的通知
    @Query("SELECT n FROM Notice n WHERE n.status = 'SCHEDULED' AND n.scheduledTime <= :now")
    List<Notice> findScheduledToPublish(@Param("now") LocalDateTime now);

    // 查询所有已计划的通知数量，将状态更新为PUBLISHED并记录发布时间时间戳，返回更新的记录数
    @Modifying
    @Query("UPDATE Notice n SET n.status = 'PUBLISHED', n.publishTime = n.scheduledTime WHERE n.status = 'SCHEDULED' AND n.scheduledTime <= :now")
    int publishScheduledNotices(@Param("now") LocalDateTime now);
}