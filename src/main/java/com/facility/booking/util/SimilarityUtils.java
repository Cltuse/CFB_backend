package com.facility.booking.util;

import java.util.Map;
import java.util.Set;

/**
 * 相似度计算工具类
 */
public class SimilarityUtils {
    
    /**
     * 计算两个向量的余弦相似度
     */
    public static double cosineSimilarity(Map<Long, Double> vectorA, Map<Long, Double> vectorB) {
        if (vectorA.isEmpty() || vectorB.isEmpty()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        // 计算点积和范数
        for (Map.Entry<Long, Double> entry : vectorA.entrySet()) {
            Long key = entry.getKey();
            Double valueA = entry.getValue();
            Double valueB = vectorB.get(key);
            
            if (valueB != null) {
                dotProduct += valueA * valueB;
            }
            normA += valueA * valueA;
        }
        
        for (Double value : vectorB.values()) {
            normB += value * value;
        }
        
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * 计算Jaccard相似度（备选算法）
     */
    public static double jaccardSimilarity(Set<Long> setA, Set<Long> setB) {
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0.0;
        }
        
        Set<Long> intersection = setA.stream()
                .filter(setB::contains)
                .collect(java.util.stream.Collectors.toSet());
        
        Set<Long> union = new java.util.HashSet<>(setA);
        union.addAll(setB);
        
        if (union.isEmpty()) {
            return 0.0;
        }
        
        return (double) intersection.size() / union.size();
    }
}