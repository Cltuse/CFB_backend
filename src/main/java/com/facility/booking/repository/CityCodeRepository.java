package com.facility.booking.repository;

import com.facility.booking.entity.CityCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CityCodeRepository extends JpaRepository<CityCode, Integer> {
    
    /**
     * 根据城市名称查询城市代码
     */
    Optional<CityCode> findByName(String name);
    
    /**
     * 根据城市代码查询城市信息
     */
    Optional<CityCode> findByCode(String code);
    
    /**
     * 模糊查询城市名称
     */
    @Query("SELECT c FROM CityCode c WHERE c.name LIKE %:name%")
    Optional<CityCode> findByNameContaining(@Param("name") String name);
}