package com.facility.booking.service;

import com.facility.booking.entity.Facility;
import com.facility.booking.entity.FacilityHotScore;
import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.UserRecommendation;
import com.facility.booking.entity.UserSimilarity;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        List<Reservation> userReservations = reservationRepository.findByUserId(userId);

        Set<Long> bookedFacilityIds = userReservations.stream()
                .map(Reservation::getFacilityId)
                .collect(Collectors.toSet());

        List<Facility> candidateFacilities = facilityRepository.findAll().stream()
                .filter(facility -> !bookedFacilityIds.contains(facility.getId()))
                .collect(Collectors.toList());

        List<UserRecommendation> recommendations = candidateFacilities.stream()
                .map(facility -> calculateRecommendationScore(userId, facility, userReservations))
                .filter(recommendation -> recommendation.getScore().compareTo(BigDecimal.valueOf(0.1)) > 0)
                .sorted((r1, r2) -> r2.getScore().compareTo(r1.getScore()))
                .limit(10)
                .collect(Collectors.toList());

        saveRecommendations(userId, recommendations);
    }

    /**
     * 计算设施的推荐分数
     */
    private UserRecommendation calculateRecommendationScore(Long userId, Facility facility, List<Reservation> userReservations) {
        UserRecommendation recommendation = new UserRecommendation();
        recommendation.setUserId(userId);
        recommendation.setFacilityId(facility.getId());

        double cfScore = calculateCFScore(userId, facility.getId());
        double hotScore = hotScoreService.getFacilityHotScore(facility.getId());
        double diversityScore = calculateDiversityScore(facility, userReservations);
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
        List<Long> similarUsers = userCFService.getSimilarUsers(userId, 20);
        if (similarUsers.isEmpty()) {
            return 0.0;
        }

        double weightedSum = 0.0;
        double similaritySum = 0.0;

        for (Long similarUserId : similarUsers) {
            List<Reservation> similarUserReservations =
                    reservationRepository.findByUserIdAndFacilityId(similarUserId, facilityId);

            if (!similarUserReservations.isEmpty()) {
                double userScore = calculateUserFacilityScore(similarUserReservations);
                double similarity = getSimilarityScore(userId, similarUserId);
                weightedSum += similarity * userScore;
                similaritySum += similarity;
            }
        }

        if (similaritySum == 0) {
            return 0.0;
        }

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
        if (reservations.isEmpty()) {
            return 0.0;
        }

        LocalDateTime now = LocalDateTime.now();
        double baseScore = Math.log1p(reservations.size()) * 2.0;

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
        if (userReservations.isEmpty()) {
            return 1.0;
        }

        Set<Long> bookedCategoryIds = userReservations.stream()
                .map(Reservation::getFacilityId)
                .map(facilityId -> 1L)
                .collect(Collectors.toSet());

        Long currentCategoryId = 1L;
        if (!bookedCategoryIds.contains(currentCategoryId)) {
            return 1.0;
        }

        return 0.3;
    }

    /**
     * 生成推荐理由
     */
    private String generateRecommendationReason(double cfScore, double hotScore, double diversityScore) {
        List<String> reasons = new ArrayList<>();

        if (cfScore > 0.3) {
            reasons.add("与您兴趣相似的用户也喜欢该设施");
        }
        if (hotScore > 5.0) {
            reasons.add("该设施近期非常热门");
        }
        if (diversityScore > 0.7) {
            reasons.add("为您推荐不同类型的设施");
        }

        if (reasons.isEmpty()) {
            return "基于您的使用习惯推荐";
        }

        return String.join("；", reasons);
    }

    /**
     * 保存推荐结果
     */
    @Transactional
    public void saveRecommendations(Long userId, List<UserRecommendation> recommendations) {
        userRecommendationRepository.deleteByUserId(userId);
        userRecommendationRepository.saveAll(recommendations);
    }

    /**
     * 获取用户的推荐列表
     */
    public List<UserRecommendation> getUserRecommendations(Long userId, int limit) {
        return userRecommendationRepository.findTopRecommendations(
                userId,
                org.springframework.data.domain.Pageable.ofSize(PageUtils.normalizeSize(limit))
        );
    }

    /**
     * 为所有用户生成推荐
     */
    @Transactional
    public void generateRecommendationsForAllUsers() {
        List<Long> userIds = reservationRepository.findAll().stream()
                .map(Reservation::getUserId)
                .distinct()
                .collect(Collectors.toList());

        for (Long userId : userIds) {
            generateRecommendationsForUser(userId);
        }
    }

    /**
     * 为活跃用户刷新推荐缓存
     */
    public void refreshActiveUserRecommendations() {
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
     * 处理冷启动问题，为新用户生成热门推荐
     */
    @Transactional
    public List<UserRecommendation> generateHotRecommendationsForNewUser(Long userId, int limit) {
        List<FacilityHotScore> hotFacilityScores = hotScoreService.getTopHotFacilities(limit);
        List<Long> hotFacilityIds = hotFacilityScores.stream()
                .map(FacilityHotScore::getFacilityId)
                .collect(Collectors.toList());

        Map<Long, Facility> facilityMap = facilityRepository.findAllById(hotFacilityIds).stream()
                .collect(Collectors.toMap(Facility::getId, facility -> facility));

        return hotFacilityScores.stream()
                .map(hotScore -> {
                    Facility facility = facilityMap.get(hotScore.getFacilityId());
                    if (facility == null) {
                        return null;
                    }

                    UserRecommendation recommendation = new UserRecommendation();
                    recommendation.setUserId(userId);
                    recommendation.setFacilityId(facility.getId());
                    recommendation.setScore(hotScore.getHotScore());
                    recommendation.setReason("该设施近期非常热门，推荐给您尝试");
                    recommendation.setCreatedAt(LocalDateTime.now());
                    recommendation.setGeneratedAt(LocalDateTime.now());
                    return recommendation;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户推荐，优先从缓存获取，未命中则实时生成
     */
    public List<UserRecommendation> getRecommendationsWithFallback(Long userId, int limit) {
        List<UserRecommendation> cachedRecommendations = getUserRecommendations(userId, limit);
        if (!cachedRecommendations.isEmpty()) {
            return cachedRecommendations;
        }

        List<Reservation> userReservations = reservationRepository.findByUserId(userId);
        if (userReservations.isEmpty()) {
            return generateHotRecommendationsForNewUser(userId, limit);
        }

        generateRecommendationsForUser(userId);
        return getUserRecommendations(userId, limit);
    }
}
