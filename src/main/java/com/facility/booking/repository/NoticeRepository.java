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
    List<Notice> findByStatus(String status);
    
    @Query("SELECT n FROM Notice n WHERE n.status = 'SCHEDULED' AND n.scheduledTime <= :now")
    List<Notice> findScheduledToPublish(@Param("now") LocalDateTime now);
    
    @Modifying
    @Query("UPDATE Notice n SET n.status = 'PUBLISHED', n.publishTime = n.scheduledTime WHERE n.status = 'SCHEDULED' AND n.scheduledTime <= :now")
    int publishScheduledNotices(@Param("now") LocalDateTime now);
}