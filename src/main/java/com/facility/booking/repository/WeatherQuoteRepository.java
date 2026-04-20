package com.facility.booking.repository;

import com.facility.booking.entity.WeatherQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WeatherQuoteRepository extends JpaRepository<WeatherQuote, Long> {
    
    /**
     * 根据天气类型查询语录列表
     */
    List<WeatherQuote> findByWeatherType(String weatherType);
    
    /**
     * 随机获取指定天气类型的一条语录
     */
    @Query(value = "SELECT * FROM weather_quote WHERE weather_type = :weatherType ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Optional<WeatherQuote> findRandomByWeatherType(@Param("weatherType") String weatherType);
    
    /**
     * 检查指定天气类型是否存在语录
     */
    boolean existsByWeatherType(String weatherType);
}