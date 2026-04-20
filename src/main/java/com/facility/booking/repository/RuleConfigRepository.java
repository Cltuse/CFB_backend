package com.facility.booking.repository;

import com.facility.booking.entity.RuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleConfigRepository extends JpaRepository<RuleConfig, Long> {
    
    // 根据设施类别ID查询当前生效的规则
    Optional<RuleConfig> findByCategoryId(Long categoryId);
    
    // 根据设施类别ID查询当前生效的规则
    Optional<RuleConfig> findByCategoryIdAndIsActiveTrue(Long categoryId);
    
    // 查询全局默认规则（category_id为NULL）
    Optional<RuleConfig> findByCategoryIdIsNull();
    
    // 查询当前生效的全局默认规则
    @Query("SELECT r FROM RuleConfig r WHERE r.categoryId IS NULL AND r.isActive = true")
    Optional<RuleConfig> findGlobalDefaultRule();
    
    // 查询全局规则的所有历史版本（按创建时间倒序）
    List<RuleConfig> findByCategoryIdIsNullOrderByCreatedAtDesc();
    
    // 查询所有规则配置，按创建时间倒序
    List<RuleConfig> findAllByOrderByCreatedAtDesc();
    
    // 根据设施类别ID查询所有历史版本
    List<RuleConfig> findByCategoryIdOrderByCreatedAtDesc(Long categoryId);
    
    // 查询所有当前生效的规则（每个类别最新版本）
    @Query("SELECT r FROM RuleConfig r WHERE r.id IN (SELECT MAX(r2.id) FROM RuleConfig r2 GROUP BY r2.categoryId)")
    List<RuleConfig> findAllCurrentRules();
    
    // 根据类别ID查询规则
    @Query("SELECT r FROM RuleConfig r WHERE r.categoryId = (SELECT c.id FROM FacilityCategory c WHERE c.categoryName = :categoryName)")
    Optional<RuleConfig> findByCategoryName(@Param("categoryName") String categoryName);
}