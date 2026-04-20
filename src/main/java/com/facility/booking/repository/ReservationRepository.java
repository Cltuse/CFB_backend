package com.facility.booking.repository;

import com.facility.booking.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    interface CategoryCountView {
        String getCategory();
        Long getTotal();
    }

    interface DailyTrendView {
        String getDate();
        Long getTotal();
        Long getPending();
        Long getApproved();
        Long getCompleted();
    }

    List<Reservation> findByUserId(Long userId);

    List<Reservation> findByFacilityId(Long facilityId);

    List<Reservation> findByStatus(String status);

    long countByStatus(String status);

    long countByCheckinStatus(String checkinStatus);

    long countByCreatedAtAfter(LocalDateTime startTime);

    long countByCreatedAtAfterAndStatus(LocalDateTime startTime, String status);

    @Query("SELECT r FROM Reservation r WHERE r.createdAt >= :startTime")
    List<Reservation> findByCreatedAtAfter(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT r FROM Reservation r WHERE r.createdAt >= :startTime AND r.facilityId IN :facilityIds")
    List<Reservation> findByCreatedAtAfterAndFacilityIdIn(@Param("startTime") LocalDateTime startTime,
                                                          @Param("facilityIds") List<Long> facilityIds);

    @Query(value = """
            SELECT COALESCE(f.category, '未分类') AS category, COUNT(*) AS total
            FROM reservation r
            LEFT JOIN facility f ON f.id = r.facility_id
            WHERE r.created_at >= :startTime
            GROUP BY COALESCE(f.category, '未分类')
            ORDER BY total DESC
            """, nativeQuery = true)
    List<CategoryCountView> countCategoryStatsAfter(@Param("startTime") LocalDateTime startTime);

    @Query(value = """
            SELECT DATE(created_at) AS date,
                   COUNT(*) AS total,
                   SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending,
                   SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved,
                   SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed
            FROM reservation
            WHERE created_at >= :startTime AND created_at < :endTime
            GROUP BY DATE(created_at)
            ORDER BY DATE(created_at)
            """, nativeQuery = true)
    List<DailyTrendView> countDailyTrends(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    @Query("SELECT r FROM Reservation r WHERE " +
            "LOWER(r.purpose) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "EXISTS(SELECT u FROM User u WHERE u.id = r.userId AND " +
            "(LOWER(u.realName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))) OR " +
            "EXISTS(SELECT f FROM Facility f WHERE f.id = r.facilityId AND " +
            "LOWER(f.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Reservation> findByKeyword(@Param("keyword") String keyword);

    List<Reservation> findByCheckinStatus(String checkinStatus);

    List<Reservation> findByStatusAndCheckinStatus(String status, String checkinStatus);

    List<Reservation> findByCheckinStatusAndStartTimeBetween(String checkinStatus,
                                                             LocalDateTime startTime,
                                                             LocalDateTime endTime);

    List<Reservation> findByCheckinStatusAndEndTimeBefore(String checkinStatus, LocalDateTime endTime);

    @Query("SELECT r FROM Reservation r WHERE r.userId = :userId AND r.status IN :statuses")
    List<Reservation> findByUserIdAndStatusIn(@Param("userId") Long userId,
                                              @Param("statuses") List<String> statuses);

    @Query("SELECT r FROM Reservation r WHERE r.verificationCode = :verificationCode")
    Optional<Reservation> findByVerificationCode(@Param("verificationCode") String verificationCode);

    @Query("SELECT r FROM Reservation r WHERE r.facilityId = :facilityId " +
            "AND r.status IN :statuses " +
            "AND ((r.startTime < :endTime AND r.endTime > :startTime))")
    List<Reservation> findConflictingReservations(@Param("facilityId") Long facilityId,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime,
                                                  @Param("statuses") List<String> statuses);

    @Query("SELECT r FROM Reservation r WHERE r.userId = :userId AND r.facilityId = :facilityId")
    List<Reservation> findByUserIdAndFacilityId(@Param("userId") Long userId,
                                                @Param("facilityId") Long facilityId);

    @Query("SELECT DISTINCT r.userId FROM Reservation r")
    List<Long> findDistinctUserIds();

    @Query("SELECT r FROM Reservation r WHERE r.startTime >= :startDate")
    List<Reservation> findByStartTimeAfter(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT DISTINCT r.userId FROM Reservation r WHERE r.startTime >= :activeDate")
    List<Long> findActiveUserIds(@Param("activeDate") LocalDateTime activeDate);

    List<Reservation> findByStatusAndCheckinStatusAndStartTimeBefore(String status,
                                                                     String checkinStatus,
                                                                     LocalDateTime startTime);
}
