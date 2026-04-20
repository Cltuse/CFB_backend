package com.facility.booking.service;

import com.facility.booking.entity.Facility;
import com.facility.booking.entity.FacilityHotScore;
import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.UserRecommendation;
import com.facility.booking.entity.UserSimilarity;
import java.math.BigDecimal;
import com.facility.booking.repository.FacilityRepository;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.repository.UserRecommendationRepository;
import com.facility.booking.repository.UserSimilarityRepository;
import com.facility.booking.util.PageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendService {

    private static final Logger logger = LoggerFactory.getLogger(RecommendService.class);

    @Autowired
    private ReservationRepository reservationRepository;
    
    @Autowired
    private FacilityRepository facilityRepository;
    
    @Autowired
    private UserRecommendationRepository userRecommendationRepository;
    
    @Autowired
    private UserSimilarityRepository userSimilarityRepository;
    
    @Autowired
    private UserCFService userCFService;
    
    @Autowired
    private HotScoreService hotScoreService;
    
    /**
     * 为指定用户生成推荐
     */
    @Transactional
    public void generateRecommendationsForUser(Long userId) {
        // 获取用户的历史预约记录
        List<Reservation> userReservations = reservationRepository.findByUserId(userId);
        
        // 获取用户已经预约过的设施ID
        Set<Long> bookedFacilityIds = userReservations.stream()
                .map(Reservation::getFacilityId)
                .collect(Collectors.toSet());
        
        // 获取所有设施
        List<Facility> allFacilities = facilityRepository.findAll();
        
        // 过滤掉用户已经预约过的设施
        List<Facility> candidateFacilities = allFacilities.stream()
                .filter(facility -> !bookedFacilityIds.contains(facility.getId()))
                .collect(Collectors.toList());
        
        // 计算每个候选设施的推荐分数
        List<UserRecommendation> recommendations = candidateFacilities.stream()
                .map(facility -> calculateRecommendationScore(userId, facility, userReservations))
                .filter(recommendation -> recommendation.getScore().compareTo(BigDecimal.valueOf(0.1)) > 0) // 过滤低分推荐
                .sorted((r1, r2) -> r2.getScore().compareTo(r1.getScore())) // 按分数降序
                .limit(10) // 限制推荐数量
                .collect(Collectors.toList());
        
        // 保存推荐结果
        saveRecommendations(userId, recommendations);
    }
    
    /**
     * 计算设施的推荐分数
     */
    private UserRecommendation calculateRecommendationScore(Long userId, Facility facility, List<Reservation> userReservations) {
        UserRecommendation recommendation = new UserRecommendation();
        recommendation.setUserId(userId);
        recommendation.setFacilityId(facility.getId());
        
        // 协同过滤分数
        double cfScore = calculateCFScore(userId, facility.getId());
        
        // 热度分数
        double hotScore = hotScoreService.getFacilityHotScore(facility.getId());
        
        // 多样性分数（避免推荐过于相似的设施）
        double diversityScore = calculateDiversityScore(facility, userReservations);
        
        // 综合分数（加权平均）
        double finalScore = cfScore * 0.6 + hotScore * 0.3 + diversityScore * 0.1;
        
        recommendation.setScore(BigDecimal.valueOf(finalScore));
        recommendation.setReason(generateRecommendationReason(cfScore, hotScore, diversityScore));
        recommendation.setCreatedAt(LocalDateTime.now());
        recommendation.setGeneratedAt(LocalDateTime.now());
        
        return recommendation;
    }
    
    /**
     * 计算协同过滤分数
     */
    private double calculateCFScore(Long userId, Long facilityId) {
        // 获取相似用户
        List<Long> similarUsers = userCFService.getSimilarUsers(userId, 20);
        
        if (similarUsers.isEmpty()) return 0.0;
        
        // 计算相似用户对该设施的评分
        double weightedSum = 0.0;
        double similaritySum = 0.0;
        
        for (Long similarUserId : similarUsers) {
            // 获取相似用户对该设施的预约记录
            List<Reservation> similarUserReservations = reservationRepository
                    .findByUserIdAndFacilityId(similarUserId, facilityId);
            
            if (!similarUserReservations.isEmpty()) {
                // 计算相似用户的评分（基于预约次数和时间）
                double userScore = calculateUserFacilityScore(similarUserReservations);
                
                // 从数据库获取真实的相似度
                double similarity = getSimilarityScore(userId, similarUserId);
                
                weightedSum += similarity * userScore;
                similaritySum += similarity;
            }
        }
        
        if (similaritySum == 0) return 0.0;
        
        return weightedSum / similaritySum;
    }
    
    /**
     * 获取用户之间的相似度分数
     */
    private double getSimilarityScore(Long userId, Long similarUserId) {
        return userSimilarityRepository.findByUserId(userId).stream()
                .filter(us -> us.getSimilarUserId().equals(similarUserId))
                .map(UserSimilarity::getSimilarityScore)
                .findFirst()
                .map(BigDecimal::doubleValue)
                .orElse(0.0);
    }
    
    /**
     * 计算用户对设施的评分
     */
    private double calculateUserFacilityScore(List<Reservation> reservations) {
        if (reservations.isEmpty()) return 0.0;
        
        LocalDateTime now = LocalDateTime.now();
        
        // 基础评分：预约次数
        double baseScore = Math.log1p(reservations.size()) * 2.0;
        
        // 时间衰减因子
        double timeFactor = reservations.stream()
                .mapToDouble(reservation -> {
                    long daysAgo = java.time.Duration.between(reservation.getStartTime(), now).toDays();
                    return Math.exp(-daysAgo / 30.0);
                })
                .sum();
        
        return baseScore + timeFactor;
    }
    
    /**
     * 计算多样性分数
     */
    private double calculateDiversityScore(Facility facility, List<Reservation> userReservations) {
        if (userReservations.isEmpty()) return 1.0; // 新用户，多样性最高
        
        // 获取用户预约过的设施类别
        Set<Long> bookedCategoryIds = userReservations.stream()
                .map(Reservation::getFacilityId)
                .map(facilityId -> {
                    // 这里需要获取设施类别，简化处理
                    return 1L; // 默认类别
                })
                .collect(Collectors.toSet());
        
        // 如果当前设施类别是新的，多样性分数更高
        Long currentCategoryId = 1L; // 简化处理
        if (!bookedCategoryIds.contains(currentCategoryId)) {
            return 1.0;
        }
        
        return 0.3; // 同类设施，多样性分数较低
    }
    
    /**
     * 生成推荐理由
     */
    private String generateRecommendationReason(double cfScore, double hotScore, double diversityScore) {
        List<String> reasons = new ArrayList<>();
        
        if (cfScore > 0.3) {
            reasons.add("与您兴趣相似的用户也喜欢此设施");
        }
        
        if (hotScore > 5.0) {
            reasons.add("此设施近期非常热门");
        }
        
        if (diversityScore > 0.7) {
            reasons.add("为您推荐不同类型的设施");
        }
        
        if (reasons.isEmpty()) {
            return "基于您的使用习惯推荐";
        }
        
        return String.join("，", reasons);
    }
    
    /**
     * 保存推荐结果
     */
    @Transactional
    public void saveRecommendations(Long userId, List<UserRecommendation> recommendations) {
        // 删除旧的推荐结果
        userRecommendationRepository.deleteByUserId(userId);
        
        // 保存新的推荐结果
        userRecommendationRepository.saveAll(recommendations);
    }
    
    /**
     * 获取用户的推荐列表
     */
    public List<UserRecommendation> getUserRecommendations(Long userId, int limit) {
        return userRecommendationRepository.findTopRecommendations(userId, org.springframework.data.domain.Pageable.ofSize(PageUtils.normalizeSize(limit)));
    }
    
    /**
     * 为所有用户生成推荐（批量处理）
     */
    @Transactional
    public void generateRecommendationsForAllUsers() {
        // 获取所有有预约记录的用户ID
        List<Long> userIds = reservationRepository.findAll().stream()
                .map(Reservation::getUserId)
                .distinct()
                .collect(Collectors.toList());
        
        // 为每个用户生成推荐
        for (Long userId : userIds) {
            generateRecommendationsForUser(userId);
        }
    }
    
    /**
     * 为活跃用户刷新推荐缓存
     */
    public void refreshActiveUserRecommendations() {
        // 获取最近7天有预约行为的活跃用户
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Long> activeUsers = reservationRepository.findActiveUserIds(sevenDaysAgo);
        
        logger.info("开始为 {} 个活跃用户刷新推荐缓存", activeUsers.size());
        
        for (Long userId : activeUsers) {
            try {
                generateRecommendationsForUser(userId);
            } catch (Exception e) {
                logger.error("为用户 {} 生成推荐失败", userId, e);
            }
        }
        
        logger.info("活跃用户推荐缓存刷新完成");
    }
    
    /**
     * 处理冷启动问题 - 为新用户生成热门推荐
     */
    @Transactional
    public List<UserRecommendation> generateHotRecommendationsForNewUser(Long userId, int limit) {
        // 获取热门设施热度评分
        List<FacilityHotScore> hotFacilityScores = hotScoreService.getTopHotFacilities(limit);
        
        // 获取热门设施ID列表
        List<Long> hotFacilityIds = hotFacilityScores.stream()
                .map(FacilityHotScore::getFacilityId)
                .collect(Collectors.toList());
        
        // 获取设施详细信息
        Map<Long, Facility> facilityMap = facilityRepository.findAllById(hotFacilityIds).stream()
                .collect(Collectors.toMap(Facility::getId, facility -> facility));
        
        return hotFacilityScores.stream()
                .map(hotScore -> {
                    Facility facility = facilityMap.get(hotScore.getFacilityId());
                    if (facility == null) return null;
                    
                    UserRecommendation recommendation = new UserRecommendation();
                    recommendation.setUserId(userId);
                    recommendation.setFacilityId(facility.getId());
                    recommendation.setScore(hotScore.getHotScore());
                    recommendation.setReason("此设施近期非常热门，推荐给您尝试");
                    recommendation.setCreatedAt(LocalDateTime.now());
                    recommendation.setGeneratedAt(LocalDateTime.now());
                    return recommendation;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取用户推荐（优先从缓存获取，未命中则实时生成）
     */
    public List<UserRecommendation> getRecommendationsWithFallback(Long userId, int limit) {
        // 先从缓存获取
        List<UserRecommendation> cachedRecommendations = getUserRecommendations(userId, limit);
        
        if (!cachedRecommendations.isEmpty()) {
            return cachedRecommendations;
        }
        
        // 检查用户是否有历史记录
        List<Reservation> userReservations = reservationRepository.findByUserId(userId);
        
        if (userReservations.isEmpty()) {
            // 新用户，返回热门推荐
            return generateHotRecommendationsForNewUser(userId, limit);
        } else {
            // 有历史记录但无缓存，实时生成推荐
            generateRecommendationsForUser(userId);
            return getUserRecommendations(userId, limit);
        }
    }
}
