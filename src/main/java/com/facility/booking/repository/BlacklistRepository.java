package com.facility.booking.repository;

import com.facility.booking.entity.Blacklist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlacklistRepository extends JpaRepository<Blacklist, Long> {
    
    // 根据用户ID查询当前有效的黑名单记录
    Optional<Blacklist> findByUserIdAndStatus(Long userId, String status);
    
    // 根据状态查询黑名单
    Page<Blacklist> findByStatus(String status, Pageable pageable);
    
    // 根据用户ID查询所有黑名单记录
    List<Blacklist> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    // 查询需要自动过期的黑名单记录
    List<Blacklist> findByStatusAndEndTimeBefore(String status, LocalDateTime time);
    
    // 根据用户姓名模糊查询
    @Query("SELECT b FROM Blacklist b WHERE b.userId IN (SELECT u.id FROM User u WHERE u.realName LIKE %:userName%)")
    Page<Blacklist> findByUserNameContaining(@Param("userName") String userName, Pageable pageable);
    
    // 查询所有黑名单记录，按创建时间倒序
    List<Blacklist> findAllByOrderByCreatedAtDesc();
    
    // 统计当前生效的黑名单数量
    long countByStatus(String status);
}