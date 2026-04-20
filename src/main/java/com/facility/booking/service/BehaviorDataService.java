package com.facility.booking.service;

import com.facility.booking.entity.UserFacilityBehavior;
import com.facility.booking.repository.UserFacilityBehaviorRepository;
import com.facility.booking.util.TimeDecayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BehaviorDataService {

    @Autowired
    private UserFacilityBehaviorRepository userFacilityBehaviorRepository;

    /**
     * 从视图获取用户-设施交互矩阵
     * @return Map<用户ID, Map<设施ID, 评分>>
     */
    public Map<Long, Map<Long, Double>> getUserFacilityMatrix() {
        List<UserFacilityBehavior> behaviors = userFacilityBehaviorRepository.findAll();

        Map<Long, Map<Long, Double>> matrix = new HashMap<>();

        for (UserFacilityBehavior behavior : behaviors) {
            Long userId = behavior.getUserId();
            Long facilityId = behavior.getFacilityId();

            // 评分计算：基于预约次数和时间衰减
            double score = calculateBehaviorScore(behavior);

            matrix.computeIfAbsent(userId, k -> new HashMap<>()).put(facilityId, score);
        }

        return matrix;
    }

    /**
     * 提取用户对设施类别的偏好权重
     * @param userId 用户ID
     * @return Map<类别名称, 偏好权重>
     */
    public Map<String, Double> getUserCategoryPreference(Long userId) {
        List<UserFacilityBehavior> userBehaviors = userFacilityBehaviorRepository.findByUserId(userId);

        Map<String, Double> categoryWeights = new HashMap<>();

        for (UserFacilityBehavior behavior : userBehaviors) {
            String category = behavior.getCategoryName();
            double score = calculateBehaviorScore(behavior);

            categoryWeights.merge(category, score, Double::sum);
        }

        // 归一化处理
        return normalizeWeights(categoryWeights);
    }

    /**
     * 计算行为评分
     */
    private double calculateBehaviorScore(UserFacilityBehavior behavior) {
        double baseScore = Math.log1p(behavior.getBookingCount()); // 对数变换

        // 时间衰减因子：最近的行为权重更高
        double timeDecay = TimeDecayUtils.calculateDecayFactor(behavior.getLastBookingTime());

        return baseScore * timeDecay;
    }

    /**
     * 归一化权重
     */
    private Map<String, Double> normalizeWeights(Map<String, Double> weights) {
        if (weights.isEmpty()) return weights;

        double maxWeight = weights.values().stream().max(Double::compare).orElse(1.0);

        return weights.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() / maxWeight
                ));
    }
}