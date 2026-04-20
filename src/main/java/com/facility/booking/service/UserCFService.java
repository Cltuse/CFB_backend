package com.facility.booking.service;

import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.UserSimilarity;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.repository.UserSimilarityRepository;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserCFService {

    @Autowired
    private ReservationRepository reservationRepository;
    
    @Autowired
    private UserSimilarityRepository userSimilarityRepository;
    
    /**
     * 计算用户相似度矩阵
     */
    @Transactional
    public void computeUserSimilarities() {
        // 获取所有用户的预约记录
        List<Reservation> allReservations = reservationRepository.findAll();
        
        // 构建用户-设施评分矩阵
        Map<Long, Map<Long, Double>> userFacilityMatrix = buildUserFacilityMatrix(allReservations);
        
        // 计算用户之间的相似度
        List<UserSimilarity> similarities = calculateSimilarities(userFacilityMatrix);
        
        // 保存相似度结果
        saveSimilarities(similarities);
    }
    
    /**
     * 构建用户-设施评分矩阵
     */
    private Map<Long, Map<Long, Double>> buildUserFacilityMatrix(List<Reservation> reservations) {
        Map<Long, Map<Long, Double>> matrix = new HashMap<>();
        
        // 按用户分组预约记录
        Map<Long, List<Reservation>> userReservations = reservations.stream()
                .collect(Collectors.groupingBy(Reservation::getUserId));
        
        // 计算每个用户对每个设施的评分
        for (Map.Entry<Long, List<Reservation>> entry : userReservations.entrySet()) {
            Long userId = entry.getKey();
            List<Reservation> userReservationList = entry.getValue();
            
            // 按设施分组计算评分
            Map<Long, Double> facilityScores = new HashMap<>();
            Map<Long, List<Reservation>> facilityReservations = userReservationList.stream()
                    .collect(Collectors.groupingBy(Reservation::getFacilityId));
            
            for (Map.Entry<Long, List<Reservation>> facilityEntry : facilityReservations.entrySet()) {
                Long facilityId = facilityEntry.getKey();
                List<Reservation> facilityReservationList = facilityEntry.getValue();
                
                // 评分基于预约次数和最近预约时间
                double score = calculateFacilityScore(facilityReservationList);
                facilityScores.put(facilityId, score);
            }
            
            matrix.put(userId, facilityScores);
        }
        
        return matrix;
    }
    
    /**
     * 计算设施评分
     */
    private double calculateFacilityScore(List<Reservation> reservations) {
        if (reservations.isEmpty()) return 0.0;
        
        // 基础评分：预约次数
        double baseScore = Math.log1p(reservations.size()) * 2.0;
        
        // 时间衰减因子：最近预约的权重更高
        LocalDateTime now = LocalDateTime.now();
        double timeFactor = reservations.stream()
                .mapToDouble(reservation -> {
                    long daysAgo = java.time.Duration.between(reservation.getStartTime(), now).toDays();
                    return Math.exp(-daysAgo / 30.0); // 30天衰减周期
                })
                .sum();
        
        return baseScore + timeFactor;
    }
    
    /**
     * 计算用户相似度
     */
    private List<UserSimilarity> calculateSimilarities(Map<Long, Map<Long, Double>> userFacilityMatrix) {
        List<UserSimilarity> similarities = new ArrayList<>();
        List<Long> userIds = new ArrayList<>(userFacilityMatrix.keySet());
        
        // 计算每对用户之间的相似度
        for (int i = 0; i < userIds.size(); i++) {
            Long user1 = userIds.get(i);
            Map<Long, Double> user1Scores = userFacilityMatrix.get(user1);
            
            for (int j = i + 1; j < userIds.size(); j++) {
                Long user2 = userIds.get(j);
                Map<Long, Double> user2Scores = userFacilityMatrix.get(user2);
                
                double similarity = calculateCosineSimilarity(user1Scores, user2Scores);
                
                // 只保存相似度较高的用户对
                if (similarity > 0.1) {
                    similarities.add(createUserSimilarity(user1, user2, similarity));
                    similarities.add(createUserSimilarity(user2, user1, similarity));
                }
            }
        }
        
        return similarities;
    }
    
    /**
     * 计算余弦相似度
     */
    private double calculateCosineSimilarity(Map<Long, Double> user1Scores, Map<Long, Double> user2Scores) {
        // 找出共同评分的设施
        Set<Long> commonFacilities = new HashSet<>(user1Scores.keySet());
        commonFacilities.retainAll(user2Scores.keySet());
        
        if (commonFacilities.isEmpty()) return 0.0;
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (Long facilityId : commonFacilities) {
            double score1 = user1Scores.get(facilityId);
            double score2 = user2Scores.get(facilityId);
            dotProduct += score1 * score2;
            norm1 += score1 * score1;
            norm2 += score2 * score2;
        }
        
        if (norm1 == 0 || norm2 == 0) return 0.0;
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * 创建用户相似度记录
     */
    private UserSimilarity createUserSimilarity(Long userId, Long similarUserId, double similarity) {
        UserSimilarity userSimilarity = new UserSimilarity();
        userSimilarity.setUserId(userId);
        userSimilarity.setSimilarUserId(similarUserId);
        userSimilarity.setSimilarityScore(BigDecimal.valueOf(similarity));
        userSimilarity.setCreatedAt(LocalDateTime.now());
        userSimilarity.setUpdatedAt(LocalDateTime.now());
        return userSimilarity;
    }
    
    /**
     * 保存相似度结果
     */
    private void saveSimilarities(List<UserSimilarity> similarities) {
        // 先删除旧的相似度数据
        Set<Long> processedUsers = similarities.stream()
                .map(UserSimilarity::getUserId)
                .collect(Collectors.toSet());
        
        for (Long userId : processedUsers) {
            userSimilarityRepository.deleteByUserId(userId);
        }
        
        // 保存新的相似度数据
        userSimilarityRepository.saveAll(similarities);
    }
    
    /**
     * 获取指定用户的相似用户列表
     */
    public List<Long> getSimilarUsers(Long userId, int limit) {
        List<UserSimilarity> similarities = userSimilarityRepository.findTopSimilarUsers(userId);
        return similarities.stream()
                .limit(limit)
                .map(UserSimilarity::getSimilarUserId)
                .collect(Collectors.toList());
    }
}