package com.facility.booking.repository;

import com.facility.booking.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    
    // 根据操作人ID查询
    Page<OperationLog> findByOperatorIdOrderByCreatedAtDesc(Long operatorId, Pageable pageable);
    
    // 根据操作类型查询
    Page<OperationLog> findByOperationTypeOrderByCreatedAtDesc(String operationType, Pageable pageable);
    
    // 根据操作人和操作类型查询
    Page<OperationLog> findByOperatorIdAndOperationTypeOrderByCreatedAtDesc(Long operatorId, String operationType, Pageable pageable);
    
    // 根据时间范围查询
    @Query("SELECT o FROM OperationLog o WHERE o.createdAt BETWEEN :startTime AND :endTime ORDER BY o.createdAt DESC")
    Page<OperationLog> findByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, Pageable pageable);
    
    // 根据操作人、操作类型、时间范围综合查询
    @Query("SELECT o FROM OperationLog o WHERE (:operatorId IS NULL OR o.operatorId = :operatorId) AND " +
           "(:operationType IS NULL OR o.operationType = :operationType) AND " +
           "(o.createdAt BETWEEN :startTime AND :endTime) ORDER BY o.createdAt DESC")
    Page<OperationLog> findByConditions(@Param("operatorId") Long operatorId, 
                                       @Param("operationType") String operationType,
                                       @Param("startTime") LocalDateTime startTime, 
                                       @Param("endTime") LocalDateTime endTime, 
                                       Pageable pageable);
    
    // 查询所有操作日志，按创建时间倒序
    Page<OperationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    // 根据目标ID查询相关操作日志
    List<OperationLog> findByTargetIdOrderByCreatedAtDesc(Long targetId);
}