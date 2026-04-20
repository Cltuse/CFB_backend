package com.facility.booking.service;

import com.facility.booking.entity.UserRecommendation;
import com.facility.booking.repository.UserRecommendationRepository;
import com.facility.booking.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecommendCacheService {

    @Autowired
    private UserRecommendationRepository userRecommendationRepository;
    
    @Autowired
    private RecommendService recommendService;
    
    /**
     * 刷新单个用户的推荐缓存
     */
    @Async
    @Transactional
    public void refreshUserRecommendations(Long userId) {
        // 删除旧的推荐结果
        userRecommendationRepository.deleteByUserId(userId);
        
        // 生成新的推荐结果
        recommendService.generateRecommendationsForUser(userId);
    }
    
    /**
     * 批量刷新近期活跃用户的推荐缓存
     */
    @Async
    @Transactional
    public void batchRefreshActiveUsers() {
        // 获取近期活跃用户ID列表
        List<Long> activeUserIds = getActiveUserIds();
        
        for (Long userId : activeUserIds) {
            refreshUserRecommendations(userId);
        }
    }
    
    /**
     * 从缓存表读取推荐结果
     */
    public List<UserRecommendation> getCachedRecommendations(Long userId, int size) {
        Pageable pageable = Pageable.ofSize(PageUtils.normalizeSize(size));
        return userRecommendationRepository.findByUserIdOrderByScoreDesc(userId, pageable);
    }
    
    /**
     * 获取活跃用户ID列表
     */
    private List<Long> getActiveUserIds() {
        // 获取最近30天内有预约行为的用户
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return userRecommendationRepository.findActiveUserIds(since);
    }
}
