package com.facility.booking.repository;

import com.facility.booking.entity.UserSimilarity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserSimilarityRepository extends JpaRepository<UserSimilarity, Long> {
    
    List<UserSimilarity> findByUserId(Long userId);
    
    @Query("SELECT us FROM UserSimilarity us WHERE us.userId = :userId ORDER BY us.similarityScore DESC")
    List<UserSimilarity> findTopSimilarUsers(@Param("userId") Long userId);
    
    @Query("SELECT us FROM UserSimilarity us WHERE us.userId = :userId AND us.similarityScore > :minScore ORDER BY us.similarityScore DESC")
    List<UserSimilarity> findTopSimilarUsersWithThreshold(@Param("userId") Long userId, @Param("minScore") Double minScore);
    
    @Query("SELECT us FROM UserSimilarity us WHERE us.userId = :userId ORDER BY us.similarityScore DESC LIMIT :limit")
    List<UserSimilarity> findTopSimilarUsersWithLimit(@Param("userId") Long userId, @Param("limit") Integer limit);
    
    @Query("SELECT us FROM UserSimilarity us WHERE us.userId = :userId ORDER BY us.similarityScore DESC")
    List<UserSimilarity> findTopSimilarUsers(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);
    
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserSimilarity us WHERE us.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
    
    boolean existsByUserIdAndSimilarUserId(@Param("userId") Long userId, @Param("similarUserId") Long similarUserId);
    
    @Modifying
    @Query(value = "INSERT INTO user_similarity (user_id, similar_user_id, similarity_score, created_at, updated_at) " +
            "VALUES (:userId, :similarUserId, :similarityScore, :createdAt, :updatedAt) " +
            "ON DUPLICATE KEY UPDATE similarity_score = VALUES(similarity_score), updated_at = VALUES(updated_at)", 
            nativeQuery = true)
    void upsertUserSimilarity(@Param("userId") Long userId, @Param("similarUserId") Long similarUserId, @Param("similarityScore") Double similarityScore, @Param("createdAt") java.time.LocalDateTime createdAt, @Param("updatedAt") java.time.LocalDateTime updatedAt);
}