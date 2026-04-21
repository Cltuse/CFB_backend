package com.facility.booking.repository;

import com.facility.booking.entity.ViolationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ViolationRecordRepository extends JpaRepository<ViolationRecord, Long> {

    Page<ViolationRecord> findByUserIdOrderByReportedTimeDesc(Long userId, Pageable pageable);

    Page<ViolationRecord> findByUserId(Long userId, Pageable pageable);

    Page<ViolationRecord> findByUserIdAndStatusOrderByReportedTimeDesc(Long userId, String status, Pageable pageable);

    @Query("SELECT v FROM ViolationRecord v WHERE v.userId = :userId AND v.reportedTime BETWEEN :startTime AND :endTime")
    Page<ViolationRecord> findByUserIdAndTimeRange(@Param("userId") Long userId,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime,
                                                   Pageable pageable);

    @Query("SELECT COUNT(v) FROM ViolationRecord v WHERE v.userId = :userId AND v.status = 'PENDING'")
    Long countActiveViolationsByUserId(@Param("userId") Long userId);

    @Query("SELECT SUM(v.penaltyPoints) FROM ViolationRecord v WHERE v.userId = :userId")
    Integer sumPenaltyPointsByUserId(@Param("userId") Long userId);

    List<ViolationRecord> findByUserIdAndStatusOrderByReportedTimeDesc(Long userId, String status);

    @Query("SELECT v FROM ViolationRecord v WHERE v.userId = :userId AND v.status = 'PENDING'")
    List<ViolationRecord> findPendingViolationsByUserId(@Param("userId") Long userId);

    Page<ViolationRecord> findAllByOrderByReportedTimeDesc(Pageable pageable);

    @Query("""
            SELECT v
            FROM ViolationRecord v
            JOIN User u ON v.userId = u.id
            WHERE LOWER(u.realName) LIKE LOWER(CONCAT('%', :userName, '%'))
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :userName, '%'))
            """)
    Page<ViolationRecord> findByUserNameContainingIgnoreCase(@Param("userName") String userName, Pageable pageable);

    Page<ViolationRecord> findByViolationType(String violationType, Pageable pageable);

    Page<ViolationRecord> findByStatus(String status, Pageable pageable);

    @Query("""
            SELECT v
            FROM ViolationRecord v
            JOIN User u ON v.userId = u.id
            WHERE (LOWER(u.realName) LIKE LOWER(CONCAT('%', :userName, '%'))
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :userName, '%')))
              AND v.violationType = :violationType
            """)
    Page<ViolationRecord> findByUserNameAndViolationType(@Param("userName") String userName,
                                                         @Param("violationType") String violationType,
                                                         Pageable pageable);

    @Query("""
            SELECT v
            FROM ViolationRecord v
            JOIN User u ON v.userId = u.id
            WHERE (LOWER(u.realName) LIKE LOWER(CONCAT('%', :userName, '%'))
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :userName, '%')))
              AND v.status = :status
            """)
    Page<ViolationRecord> findByUserNameAndStatus(@Param("userName") String userName,
                                                  @Param("status") String status,
                                                  Pageable pageable);

    Page<ViolationRecord> findByViolationTypeAndStatus(String violationType, String status, Pageable pageable);

    @Query("""
            SELECT v
            FROM ViolationRecord v
            JOIN User u ON v.userId = u.id
            WHERE (LOWER(u.realName) LIKE LOWER(CONCAT('%', :userName, '%'))
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :userName, '%')))
              AND v.violationType = :violationType
              AND v.status = :status
            """)
    Page<ViolationRecord> findByFilters(@Param("userName") String userName,
                                        @Param("violationType") String violationType,
                                        @Param("status") String status,
                                        Pageable pageable);

    @Query("SELECT COUNT(v) FROM ViolationRecord v WHERE v.status = 'PENDING'")
    Long countByStatusPending();

    @Query("SELECT COALESCE(SUM(v.penaltyPoints), 0) FROM ViolationRecord v")
    Integer sumAllPenaltyPoints();

    @Query("SELECT COALESCE(SUM(v.penaltyPoints), 0) FROM ViolationRecord v WHERE v.status = 'PROCESSED'")
    Integer sumAllProcessedPenaltyPoints();

    @Query("SELECT COUNT(v) FROM ViolationRecord v WHERE v.status = :status")
    Long countByStatus(@Param("status") String status);

    Page<ViolationRecord> findByReportedByOrderByReportedTimeDesc(Long reportedBy, Pageable pageable);

    Page<ViolationRecord> findByReportedByIsNotNullOrderByReportedTimeDesc(Pageable pageable);

    Page<ViolationRecord> findByReportedByAndStatusOrderByReportedTimeDesc(Long reportedBy, String status, Pageable pageable);

    Page<ViolationRecord> findByReportedByAndViolationTypeOrderByReportedTimeDesc(Long reportedBy, String violationType, Pageable pageable);

    Page<ViolationRecord> findByReportedByAndViolationTypeAndStatusOrderByReportedTimeDesc(Long reportedBy,
                                                                                           String violationType,
                                                                                           String status,
                                                                                           Pageable pageable);

    List<ViolationRecord> findByReservationIdInOrderByReportedTimeDesc(List<Long> reservationIds);

    @Query("SELECT COUNT(v) FROM ViolationRecord v WHERE v.userId = :userId")
    Integer countAllViolationsByUserId(@Param("userId") Long userId);

    boolean existsByReservationIdAndViolationType(Long reservationId, String violationType);

    @Query("SELECT COALESCE(SUM(v.penaltyPoints), 0) FROM ViolationRecord v WHERE v.userId = :userId AND v.status = 'PROCESSED'")
    Integer sumProcessedPenaltyPointsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(v) FROM ViolationRecord v WHERE v.userId = :userId AND v.status = 'PROCESSED'")
    Integer countProcessedViolationsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(v) FROM ViolationRecord v WHERE v.userId = :userId AND v.status = :status AND v.reportedTime >= :startTime")
    Integer countRecentProcessedViolations(@Param("userId") Long userId,
                                           @Param("status") String status,
                                           @Param("startTime") LocalDateTime startTime);
}
