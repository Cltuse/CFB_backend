package com.facility.booking.repository;

import com.facility.booking.entity.FacilityCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 设备类别数据访问接口
 */
@Repository
public interface FacilityCategoryRepository extends JpaRepository<FacilityCategory, Long> {

    /**
     * 根据类别名称查找
     */
    Optional<FacilityCategory> findByCategoryName(String categoryName);

    
    /**
     * 根据状态查找
     */
    List<FacilityCategory> findByStatus(String status);

    /**
     * 检查类别名称是否存在
     */
    boolean existsByCategoryName(String categoryName);

    
    /**
     * 根据状态查找并按排序顺序排序
     */
    @Query("SELECT ec FROM FacilityCategory ec WHERE ec.status = ?1 ORDER BY ec.sortOrder ASC, ec.id ASC")
    List<FacilityCategory> findByStatusOrderBySortOrderAsc(String status);

    /**
     * 查找所有并按排序顺序排序
     */
    @Query("SELECT ec FROM FacilityCategory ec ORDER BY ec.sortOrder ASC, ec.id ASC")
    List<FacilityCategory> findAllOrderBySortOrderAsc();

    /**
     * 根据类别名称模糊查询
     */
    @Query("SELECT ec FROM FacilityCategory ec WHERE ec.categoryName LIKE %:keyword% ORDER BY ec.sortOrder ASC, ec.id ASC")
    List<FacilityCategory> findByCategoryNameContainingOrderBySortOrderAsc(String keyword);

    /**
     * 根据类别名称模糊查询（不区分大小写）
     */
    @Query("SELECT ec FROM FacilityCategory ec WHERE LOWER(ec.categoryName) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY ec.sortOrder ASC, ec.id ASC")
    List<FacilityCategory> findByCategoryNameContainingIgnoreCaseOrderBySortOrderAsc(String keyword);

    /**
     * 根据描述模糊查询
     */
    @Query("SELECT ec FROM FacilityCategory ec WHERE ec.description LIKE %:keyword% ORDER BY ec.sortOrder ASC, ec.id ASC")
    List<FacilityCategory> findByDescriptionContainingOrderBySortOrderAsc(String keyword);

    /**
     * 综合模糊查询（类别名称或描述）
     */
    @Query("SELECT ec FROM FacilityCategory ec WHERE " +
           "LOWER(ec.categoryName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(ec.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY ec.sortOrder ASC, ec.id ASC")
    List<FacilityCategory> findByKeywordContainingIgnoreCaseOrderBySortOrderAsc(String keyword);

    /**
     * 分页查询所有类别
     */
    Page<FacilityCategory> findAllByOrderBySortOrderAsc(Pageable pageable);

    /**
     * 分页根据状态查询
     */
    @Query("SELECT ec FROM FacilityCategory ec WHERE ec.status = ?1 ORDER BY ec.sortOrder ASC, ec.id ASC")
    Page<FacilityCategory> findByStatusOrderBySortOrderAsc(String status, Pageable pageable);

    /**
     * 分页综合模糊查询（类别名称或描述）
     */
    @Query("SELECT ec FROM FacilityCategory ec WHERE " +
           "LOWER(ec.categoryName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(ec.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY ec.sortOrder ASC, ec.id ASC")
    Page<FacilityCategory> findByKeywordContainingIgnoreCaseOrderBySortOrderAsc(String keyword, Pageable pageable);

    /**
     * 分页根据类别名称模糊查询
     */
    @Query("SELECT ec FROM FacilityCategory ec WHERE LOWER(ec.categoryName) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY ec.sortOrder ASC, ec.id ASC")
    Page<FacilityCategory> findByCategoryNameContainingIgnoreCaseOrderBySortOrderAsc(String keyword, Pageable pageable);
}