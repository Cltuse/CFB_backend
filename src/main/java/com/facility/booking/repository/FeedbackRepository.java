package com.facility.booking.repository;

import com.facility.booking.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // 分页根据用户ID查询反馈，按创建时间降序
    Page<Feedback> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 分页根据用户ID查询反馈
    Page<Feedback> findByUserId(Long userId, Pageable pageable);

    // 分页根据状态查询反馈
    Page<Feedback> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    // 分页根据类型查询反馈
    Page<Feedback> findByTypeOrderByCreatedAtDesc(String type, Pageable pageable);

    // 分页根据用户ID和关键词查询反馈
    @Query("SELECT f FROM Feedback f WHERE f.userId = :userId AND (f.title LIKE %:keyword% OR f.content LIKE %:keyword%)")
    Page<Feedback> findByUserIdAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword,
            Pageable pageable);

    // 分页根据用户ID和状态查询反馈数量
    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.userId = :userId AND f.status = :status")
    Long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    // 分页根据用户ID和状态查询反馈
    List<Feedback> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    // 管理员获取所有反馈
    Page<Feedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 根据状态和类型查询反馈，按创建时间降序
    @Query("SELECT f FROM Feedback f WHERE f.status = :status AND f.type = :type ORDER BY f.createdAt DESC")
    Page<Feedback> findByStatusAndTypeOrderByCreatedAtDesc(@Param("status") String status, @Param("type") String type,
            Pageable pageable);

    // 根据关键词查询反馈，按创建时间降序
    @Query("SELECT f FROM Feedback f WHERE f.title LIKE %:keyword% OR f.content LIKE %:keyword% ORDER BY f.createdAt DESC")
    Page<Feedback> findByKeywordOrderByCreatedAtDesc(@Param("keyword") String keyword, Pageable pageable);

    // 根据状态和关键词查询反馈，按创建时间降序
    @Query("SELECT f FROM Feedback f WHERE f.status = :status AND (f.title LIKE %:keyword% OR f.content LIKE %:keyword%) ORDER BY f.createdAt DESC")
    Page<Feedback> findByStatusAndKeywordOrderByCreatedAtDesc(@Param("status") String status,
            @Param("keyword") String keyword, Pageable pageable);

    // 根据类型和关键词查询反馈，按创建时间降序
    @Query("SELECT f FROM Feedback f WHERE f.type = :type AND (f.title LIKE %:keyword% OR f.content LIKE %:keyword%) ORDER BY f.createdAt DESC")
    Page<Feedback> findByTypeAndKeywordOrderByCreatedAtDesc(@Param("type") String type,
            @Param("keyword") String keyword, Pageable pageable);

    // 根据状态、类型和关键词查询反馈，按创建时间降序
    @Query("SELECT f FROM Feedback f WHERE f.status = :status AND f.type = :type AND (f.title LIKE %:keyword% OR f.content LIKE %:keyword%) ORDER BY f.createdAt DESC")
    Page<Feedback> findByStatusAndTypeAndKeywordOrderByCreatedAtDesc(@Param("status") String status,
            @Param("type") String type, @Param("keyword") String keyword, Pageable pageable);

    Long countByStatus(String status);
}