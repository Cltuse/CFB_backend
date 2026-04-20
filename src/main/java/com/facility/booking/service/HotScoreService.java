package com.facility.booking.service;

import com.facility.booking.entity.FacilityHotScore;
import com.facility.booking.entity.Reservation;
import com.facility.booking.repository.FacilityHotScoreRepository;
import com.facility.booking.repository.ReservationRepository;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HotScoreService {

    @Autowired
    private ReservationRepository reservationRepository;
    
    @Autowired
    private FacilityHotScoreRepository facilityHotScoreRepository;

    /**
     * 计算所有设施的热度评分
     */
    @Transactional
    public void calculateAllFacilityHotScores() {
        // 获取所有预约记录
        List<Reservation> allReservations = reservationRepository.findAll();
        
        // 按设施分组计算热度评分
        Map<Long, List<Reservation>> facilityReservations = allReservations.stream()
                .collect(Collectors.groupingBy(Reservation::getFacilityId));
        
        // 计算每个设施的热度评分
        List<FacilityHotScore> hotScores = facilityReservations.entrySet().stream()
                .map(entry -> calculateFacilityHotScore(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        
        // 保存热度评分
        saveHotScores(hotScores);
    }

    /**
     * 保存热度评分
     */
    @Transactional
    private void saveHotScores(List<FacilityHotScore> hotScores) {
        // 先删除所有旧的热度评分
        facilityHotScoreRepository.deleteAllHotScores();

        // 保存新的热度评分
        facilityHotScoreRepository.saveAll(hotScores);
    }

    /**
     * 计算单个设施的热度评分
     */
    private FacilityHotScore calculateFacilityHotScore(Long facilityId, List<Reservation> reservations) {
        FacilityHotScore hotScore = new FacilityHotScore();
        hotScore.setFacilityId(facilityId);
        hotScore.setTotalBookings(reservations.size());
        
        // 热度评分计算公式
        double score = calculateHotScore(reservations);
        hotScore.setHotScore(BigDecimal.valueOf(score));
        hotScore.setCreatedAt(LocalDateTime.now());
        hotScore.setUpdatedAt(LocalDateTime.now());
        
        return hotScore;
    }
    
    /**
     * 热度评分计算逻辑
     */
    private double calculateHotScore(List<Reservation> reservations) {
        if (reservations.isEmpty()) return 0.0;
        
        LocalDateTime now = LocalDateTime.now();
        
        // 基础热度：预约总数（对数变换避免极端值）
        double baseScore = Math.log1p(reservations.size()) * 10.0;
        
        // 近期热度：最近30天的预约权重更高
        double recentScore = reservations.stream()
                .mapToDouble(reservation -> {
                    long daysAgo = java.time.Duration.between(reservation.getStartTime(), now).toDays();
                    if (daysAgo <= 30) {
                        return Math.exp(-daysAgo / 15.0); // 15天衰减周期
                    }
                    return 0.0;
                })
                .sum() * 5.0;
        
        // 时间分布：均匀分布的预约比集中预约更有价值
        double timeDistributionScore = calculateTimeDistributionScore(reservations);
        
        return baseScore + recentScore + timeDistributionScore;
    }
    
    /**
     * 计算时间分布评分
     */
    private double calculateTimeDistributionScore(List<Reservation> reservations) {
        if (reservations.size() < 2) return 0.0;
        
        // 按月份统计预约分布
        Map<Integer, Long> monthlyCounts = reservations.stream()
                .collect(Collectors.groupingBy(
                    reservation -> reservation.getStartTime().getMonthValue(),
                    Collectors.counting()
                ));
        
        // 计算分布的均匀性（熵值）
        double entropy = 0.0;
        double total = reservations.size();
        
        for (Long count : monthlyCounts.values()) {
            double probability = count / total;
            if (probability > 0) {
                entropy -= probability * Math.log(probability);
            }
        }
        
        // 熵值越高，分布越均匀，评分越高
        return entropy * 3.0;
    }
    

    
    /**
     * 获取设施的热度评分
     */
    public Double getFacilityHotScore(Long facilityId) {
        return facilityHotScoreRepository.findById(facilityId)
                .map(FacilityHotScore::getHotScore)
                .map(BigDecimal::doubleValue)
                .orElse(0.0);
    }

    /**
     * 获取热门设施列表
     */
    public List<Long> getHotFacilities(int limit) {
        List<FacilityHotScore> hotScores = facilityHotScoreRepository.findTopHotFacilities(limit);
        return hotScores.stream()
                .map(FacilityHotScore::getFacilityId)
                .collect(Collectors.toList());
    }

    /**
     * 获取热门设施详情列表
     */
    public List<FacilityHotScore> getHotFacilityScores(int limit) {
        return facilityHotScoreRepository.findTopHotFacilities(limit);
    }
    
    /**
     * 获取热门设施列表（用于推荐系统）
     */
    public List<FacilityHotScore> getTopHotFacilities(int limit) {
        return facilityHotScoreRepository.findTopHotFacilities(limit);
    }
}