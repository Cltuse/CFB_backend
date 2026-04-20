package com.facility.booking.repository;

import com.facility.booking.entity.Facility;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacilityRepository extends JpaRepository<Facility, Long> {
    List<Facility> findByStatus(String status);

    List<Facility> findByNameContaining(String name);

    List<Facility> findByCategory(String category);

    // 分页查询所有设备
    Page<Facility> findAll(Pageable pageable);

    // 分页搜索设备（按名称、型号、类别、位置）
    @Query("SELECT e FROM Facility e WHERE " +
           "e.name LIKE %:keyword% OR " +
           "e.model LIKE %:keyword% OR " +
           "e.category LIKE %:keyword% OR " +
           "e.location LIKE %:keyword%")
    Page<Facility> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // 按状态分页查询
    Page<Facility> findByStatus(String status, Pageable pageable);

    // 按类别分页查询
    Page<Facility> findByCategory(String category, Pageable pageable);

    // 使用悲观锁查询设施（用于并发控制）
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Facility f WHERE f.id = :id")
    Optional<Facility> findByIdWithLock(@Param("id") Long id);
}
