package com.facility.booking.service;

import com.facility.booking.util.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpLocationService {

    @Value("${ip.location.api}")
    private String ipLocationApi;
    
    // 缓存IP对应的城市信息，每小时更新一次
    private final ConcurrentHashMap<String, IpLocationCache> ipCache = new ConcurrentHashMap<>();
    
    private static class IpLocationCache {
        String city;
        LocalDateTime timestamp;
        
        IpLocationCache(String city) {
            this.city = city;
            this.timestamp = LocalDateTime.now();
        }
        
        boolean isExpired() {
            return LocalDateTime.now().isAfter(timestamp.plusHours(1));
        }
    }

    /**
     * 根据IP地址获取地理位置
     */
    public String getLocationByIp(String ip) {
        // 检查是否是本地IP
        if (isLocalIp(ip)) {
            return "北京"; // 本地IP返回默认城市
        }
        
        // 检查缓存
        IpLocationCache cache = ipCache.get(ip);
        if (cache != null && !cache.isExpired()) {
            return cache.city;
        }
        
        try {
            String url = ipLocationApi + ip + "&json=true";
            String response = HttpUtil.get(url);
            
            // 使用Spring Boot自带的ObjectMapper解析JSON
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(response);
            
            String city = jsonNode.get("city").asText();
            String result = city.isEmpty() ? "北京" : city;
            
            // 更新缓存
            ipCache.put(ip, new IpLocationCache(result));
            return result;
        } catch (Exception e) {
            return "北京"; // 默认返回北京
        }
    }
    
    /**
     * 判断是否为本地IP
     */
    private boolean isLocalIp(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "localhost".equals(ip);
    }

    /**
     * 从请求中获取客户端IP地址
     */
    public String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}