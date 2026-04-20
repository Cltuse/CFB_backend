package com.facility.booking.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 时间衰减工具类
 */
public class TimeDecayUtils {
    
    /**
     * 计算时间衰减因子（按天衰减）
     * @param eventTime 事件发生时间
     * @return 衰减因子（0-1之间）
     */
    public static double calculateDecayFactor(LocalDateTime eventTime) {
        if (eventTime == null) {
            return 0.0;
        }
        
        LocalDateTime now = LocalDateTime.now();
        long daysAgo = ChronoUnit.DAYS.between(eventTime, now);
        
        // 使用指数衰减：半衰期为30天
        double halfLife = 30.0;
        return Math.exp(-Math.log(2) * daysAgo / halfLife);
    }
    
    /**
     * 计算基于时间的权重衰减
     * @param eventTime 事件发生时间
     * @param halfLifeDays 半衰期天数
     * @return 衰减后的权重
     */
    public static double calculateDecayFactor(LocalDateTime eventTime, double halfLifeDays) {
        if (eventTime == null) {
            return 0.0;
        }
        
        LocalDateTime now = LocalDateTime.now();
        long daysAgo = ChronoUnit.DAYS.between(eventTime, now);
        
        return Math.exp(-Math.log(2) * daysAgo / halfLifeDays);
    }
}