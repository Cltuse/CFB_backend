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
    interface TypeCountView {
        String getMaintenanceType();
        Long getTotal();
    }

    interface TypeDurationView {
        String getMaintenanceType();
        Double getAvgDuration();
    }

    interface FacilityFaultView {
        Long getFacilityId();
        String getFacilityName();
        Long getFaultCount();
    }

    List<Maintenance> findByFacilityId(Long facilityId);

    List<Maintenance> findByStatus(String status);
    
    List<Maintenance> findByMaintainerId(Long maintainerId);

    List<Maintenance> findByCreatedAtAfter(LocalDateTime startTime);

    long countByStatus(String status);

    @Query("SELECT COALESCE(m.maintenanceType, 'OTHER') AS maintenanceType, COUNT(m) AS total " +
            "FROM Maintenance m WHERE m.createdAt >= :startTime " +
            "GROUP BY COALESCE(m.maintenanceType, 'OTHER')")
    List<TypeCountView> countByTypeAfter(@Param("startTime") LocalDateTime startTime);

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

    List<Maintenance> findByStatusAndStartTimeLessThanEqualAndStartTimeAfter(
            String status, LocalDateTime latestStartTime, LocalDateTime earliestStartTime);
}
