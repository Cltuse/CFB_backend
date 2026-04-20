package com.facility.booking.common;

/**
 * 推荐相关常量
 */
public class RecommendConstants {

    // 默认推荐数量
    public static final int DEFAULT_RECOMMEND_SIZE = 10;
    public static final int MAX_RECOMMEND_SIZE = 20;

    // 缓存过期时间（小时）
    public static final int CACHE_EXPIRE_HOURS = 24;

    // 相似度阈值
    public static final double SIMILARITY_THRESHOLD = 0.1;

    // 推荐理由模板
    public static final String REASON_CF = "与您兴趣相似的用户也喜欢此设施";
    public static final String REASON_HOT = "此设施近期非常热门";
    public static final String REASON_PREF = "符合您的设施类别偏好";
    public static final String REASON_HYBRID = "综合您的兴趣和设施热度推荐";

    // 类别名称映射
    public static final java.util.Map<String, String> CATEGORY_MAPPING = java.util.Map.of(
            "study", "学习类",
            "sports", "运动类",
            "entertainment", "娱乐类",
            "meeting", "会议类"
    );
}