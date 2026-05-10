package com.facility.booking.repository;

import com.facility.booking.entity.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {
    // 维计类型视图
    interface TypeCountView {
        String getMaintenanceType();

        Long getTotal();
    }

    // 维计类型视图-平均持续视图
    interface TypeDurationView {
        String getMaintenanceType();

        Double getAvgDuration();
    }

    // 维计设施视图
    interface FacilityFaultView {
        Long getFacilityId();

        String getFacilityName();

        Long getFaultCount();
    }

    // 根据设施ID查询所有维护记录
    List<Maintenance> findByFacilityId(Long facilityId);

    // 根据状态查询所有维护记录
    List<Maintenance> findByStatus(String status);

    // 根据维护人ID查询所有维护记录
    List<Maintenance> findByMaintainerId(Long maintainerId);

    // 根据创建时间查询所有维护记录
    List<Maintenance> findByCreatedAtAfter(LocalDateTime startTime);

    // 根据状态查询所有维护记录数量
    long countByStatus(String status);

    // 根据类型查询所有维护记录数量
    @Query("SELECT COALESCE(m.maintenanceType, 'OTHER') AS maintenanceType, COUNT(m) AS total " +
            "FROM Maintenance m WHERE m.createdAt >= :startTime " +
            "GROUP BY COALESCE(m.maintenanceType, 'OTHER')")
    List<TypeCountView> countByTypeAfter(@Param("startTime") LocalDateTime startTime);

    // 根据类型查询所有维护记录平均持续时间
    @Query(value = """
            SELECT COALESCE(maintenance_type, 'OTHER') AS maintenanceType,
                   AVG(TIMESTAMPDIFF(HOUR, start_time, end_time)) AS avgDuration
            FROM maintenance
            WHERE created_at >= :startTime
              AND status = 'COMPLETED'
              AND start_time IS NOT NULL
              AND end_time IS NOT NULL
            GROUP BY COALESCE(maintenance_type, 'OTHER')
            """, nativeQuery = true)
    List<TypeDurationView> averageDurationByTypeAfter(@Param("startTime") LocalDateTime startTime);

    // 根据设施ID查询所有维护记录数量
    @Query(value = """
            SELECT m.facility_id AS facilityId,
                   COALESCE(f.name, 'Unknown facility') AS facilityName,
                   COUNT(*) AS faultCount
            FROM maintenance m
            LEFT JOIN facility f ON f.id = m.facility_id
            WHERE m.created_at >= :startTime
            GROUP BY m.facility_id, f.name
            ORDER BY faultCount DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<FacilityFaultView> findTopFacilityFaultsAfter(@Param("startTime") LocalDateTime startTime,
            @Param("limit") int limit);

    // 根据状态查询所有维护记录数量
    List<Maintenance> findByStatusAndStartTimeLessThanEqual(String status, LocalDateTime latestStartTime);
}
