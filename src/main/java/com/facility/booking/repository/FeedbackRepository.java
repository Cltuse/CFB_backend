package com.facility.booking.repository;

import com.facility.booking.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    
    Page<Feedback> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    Page<Feedback> findByUserId(Long userId, Pageable pageable);
    
    Page<Feedback> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    
    Page<Feedback> findByTypeOrderByCreatedAtDesc(String type, Pageable pageable);
    
    @Query("SELECT f FROM Feedback f WHERE f.userId = :userId AND (f.title LIKE %:keyword% OR f.content LIKE %:keyword%)")
    Page<Feedback> findByUserIdAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword, Pageable pageable);
    
    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.userId = :userId AND f.status = :status")
    Long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
    
    List<Feedback> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    
    // 管理员获取所有反馈
    Page<Feedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    @Query("SELECT f FROM Feedback f WHERE f.status = :status AND f.type = :type ORDER BY f.createdAt DESC")
    Page<Feedback> findByStatusAndTypeOrderByCreatedAtDesc(@Param("status") String status, @Param("type") String type, Pageable pageable);
    
    @Query("SELECT f FROM Feedback f WHERE f.title LIKE %:keyword% OR f.content LIKE %:keyword% ORDER BY f.createdAt DESC")
    Page<Feedback> findByKeywordOrderByCreatedAtDesc(@Param("keyword") String keyword, Pageable pageable);
    
    @Query("SELECT f FROM Feedback f WHERE f.status = :status AND (f.title LIKE %:keyword% OR f.content LIKE %:keyword%) ORDER BY f.createdAt DESC")
    Page<Feedback> findByStatusAndKeywordOrderByCreatedAtDesc(@Param("status") String status, @Param("keyword") String keyword, Pageable pageable);
    
    @Query("SELECT f FROM Feedback f WHERE f.type = :type AND (f.title LIKE %:keyword% OR f.content LIKE %:keyword%) ORDER BY f.createdAt DESC")
    Page<Feedback> findByTypeAndKeywordOrderByCreatedAtDesc(@Param("type") String type, @Param("keyword") String keyword, Pageable pageable);
    
    @Query("SELECT f FROM Feedback f WHERE f.status = :status AND f.type = :type AND (f.title LIKE %:keyword% OR f.content LIKE %:keyword%) ORDER BY f.createdAt DESC")
    Page<Feedback> findByStatusAndTypeAndKeywordOrderByCreatedAtDesc(@Param("status") String status, @Param("type") String type, @Param("keyword") String keyword, Pageable pageable);
    
    Long countByStatus(String status);
}