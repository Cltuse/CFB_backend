package com.facility.booking.repository;

import com.facility.booking.entity.UserRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserRecommendationRepository extends JpaRepository<UserRecommendation, Long> {
    
    List<UserRecommendation> findByUserId(Long userId);
    
    List<UserRecommendation> findByUserIdOrderByScoreDesc(Long userId, org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT ur FROM UserRecommendation ur WHERE ur.userId = :userId ORDER BY ur.score DESC")
    List<UserRecommendation> findTopRecommendations(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT ur FROM UserRecommendation ur WHERE ur.userId = :userId ORDER BY ur.score DESC LIMIT :limit")
    List<UserRecommendation> findTopRecommendationsWithLimit(@Param("userId") Long userId, @Param("limit") Integer limit);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserRecommendation ur WHERE ur.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
    
    boolean existsByUserIdAndFacilityId(@Param("userId") Long userId, @Param("facilityId") Long facilityId);
    
    @Query("SELECT DISTINCT ur.userId FROM UserRecommendation ur WHERE ur.createdAt >= :since")
    List<Long> findActiveUserIds(@Param("since") LocalDateTime since);
}