package com.facility.booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推荐算法参数配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "recommend")
public class RecommendConfig {

    // 混合推荐权重
    private double weightUserCf = 0.6;    // 协同过滤权重
    private double weightHot = 0.3;       // 热度权重
    private double weightPref = 0.1;      // 类别偏好权重

    // 相似度阈值
    private double similarityThreshold = 0.1;

    // 推荐数量配置
    private int defaultRecommendSize = 10;
    private int maxRecommendSize = 20;

    // 缓存配置
    private int cacheExpireHours = 24;
    private int batchRefreshSize = 100;
}