package com.facility.booking.controller;

import com.facility.booking.common.Result;
import com.facility.booking.entity.Facility;
import com.facility.booking.entity.UserRecommendation;
import com.facility.booking.repository.FacilityRepository;
import com.facility.booking.service.RecommendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommend")
public class RecommendationController {

    @Autowired
    private RecommendService recommendService;
    
    @Autowired
    private FacilityRepository facilityRepository;
    
    /**
     * 获取用户的推荐设施列表
     */
    @GetMapping("/user/{userId}")
    public Result getUserRecommendations(@PathVariable Long userId) {
        try {
            // 获取推荐结果 using fallback method for better reliability
            List<UserRecommendation> recommendations = recommendService.getRecommendationsWithFallback(userId, 10);
            
            // 获取推荐设施详情
            List<Long> facilityIds = recommendations.stream()
                    .map(UserRecommendation::getFacilityId)
                    .collect(Collectors.toList());
            
            Map<Long, Facility> facilityMap = facilityRepository.findAllById(facilityIds).stream()
                    .collect(Collectors.toMap(Facility::getId, facility -> facility));
            
            // 构建返回结果
            List<Map<String, Object>> result = recommendations.stream()
                    .map(recommendation -> {
                        Facility facility = facilityMap.get(recommendation.getFacilityId());
                        return Map.of(
                            "facility", facility,
                            "score", recommendation.getScore(),
                            "reason", recommendation.getReason()
                        );
                    })
                    .collect(Collectors.toList());
            
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取推荐失败: " + e.getMessage());
        }
    }
    
    /**
     * 为指定用户重新生成推荐
     */
    @PostMapping("/user/{userId}/regenerate")
    public Result regenerateRecommendations(@PathVariable Long userId) {
        try {
            recommendService.generateRecommendationsForUser(userId);
            return Result.success("推荐重新生成成功");
        } catch (Exception e) {
            return Result.error("重新生成推荐失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取热门设施推荐（基于热度评分）
     */
    @GetMapping("/hot")
    public Result getHotRecommendations(@RequestParam(defaultValue = "10") int limit) {
        try {
            // 这里需要实现获取热门设施的逻辑
            // 暂时返回空列表，实际实现需要完善HotScoreService
            return Result.success(List.of());
        } catch (Exception e) {
            return Result.error("获取热门推荐失败: " + e.getMessage());
        }
    }
}