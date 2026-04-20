package com.facility.booking.repository;

import com.facility.booking.entity.FacilityHotScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacilityHotScoreRepository extends JpaRepository<FacilityHotScore, Long> {
    
    @Modifying
    @Query("DELETE FROM FacilityHotScore")
    void deleteAllHotScores();

    @Query("SELECT fhs FROM FacilityHotScore fhs ORDER BY fhs.hotScore DESC")
    List<FacilityHotScore> findAllOrderByHotScoreDesc();

    @Query("SELECT fhs FROM FacilityHotScore fhs ORDER BY fhs.hotScore DESC LIMIT :limit")
    List<FacilityHotScore> findTopHotFacilities(@Param("limit") int limit);
    
    @Query("SELECT fhs FROM FacilityHotScore fhs ORDER BY fhs.hotScore DESC")
    List<FacilityHotScore> findAllByOrderByHotScoreDesc(org.springframework.data.domain.Pageable pageable);
}