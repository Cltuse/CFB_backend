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

    List<Facility> findByMaintainerId(Long maintainerId);

    Page<Facility> findAll(Pageable pageable);

    Page<Facility> findByMaintainerId(Long maintainerId, Pageable pageable);

    @Query("SELECT e FROM Facility e WHERE " +
           "e.name LIKE %:keyword% OR " +
           "e.model LIKE %:keyword% OR " +
           "e.category LIKE %:keyword% OR " +
           "e.location LIKE %:keyword%")
    Page<Facility> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT e FROM Facility e WHERE e.maintainerId = :maintainerId AND (" +
           "e.name LIKE %:keyword% OR " +
           "e.model LIKE %:keyword% OR " +
           "e.category LIKE %:keyword% OR " +
           "e.location LIKE %:keyword%)")
    Page<Facility> searchByKeywordAndMaintainerId(@Param("keyword") String keyword,
                                                  @Param("maintainerId") Long maintainerId,
                                                  Pageable pageable);

    Page<Facility> findByStatus(String status, Pageable pageable);

    Page<Facility> findByCategory(String category, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Facility f WHERE f.id = :id")
    Optional<Facility> findByIdWithLock(@Param("id") Long id);
}
