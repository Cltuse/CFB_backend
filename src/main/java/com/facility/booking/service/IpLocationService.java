package com.facility.booking.service;

import com.facility.booking.util.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpLocationService {

    public record LocationInfo(String ipAddress, String city, String regionAddress) {}

    @Value("${ip.location.api}")
    private String ipLocationApi;

    private final ConcurrentHashMap<String, IpLocationCache> ipCache = new ConcurrentHashMap<>();

    private static class IpLocationCache {
        private final String city;
        private final String regionAddress;
        private final LocalDateTime timestamp;

        private IpLocationCache(String city, String regionAddress) {
            this.city = city;
            this.regionAddress = regionAddress;
            this.timestamp = LocalDateTime.now();
        }

        private boolean isExpired() {
            return LocalDateTime.now().isAfter(timestamp.plusHours(1));
        }
    }

    public String getLocationByIp(String ip) {
        return getLocationInfoByIp(ip).city();
    }

    public LocationInfo getLocationInfoByIp(String ip) {
        String normalizedIp = normalizeIp(ip);

        if (isLocalIp(normalizedIp)) {
            return new LocationInfo(normalizedIp, "北京", "本机访问 / 局域网环境");
        }

        IpLocationCache cache = ipCache.get(normalizedIp);
        if (cache != null && !cache.isExpired()) {
            return new LocationInfo(normalizedIp, cache.city, cache.regionAddress);
        }

        try {
            String response = HttpUtil.get(ipLocationApi + normalizedIp + "&json=true");
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(response);

            String city = readText(jsonNode, "city");
            String province = readText(jsonNode, "pro");
            String region = readText(jsonNode, "region");
            String addr = readText(jsonNode, "addr");
            String resultCity = city.isEmpty() ? "北京" : city;
            String regionAddress = buildRegionAddress(province, city, region, addr, resultCity);

            ipCache.put(normalizedIp, new IpLocationCache(resultCity, regionAddress));
            return new LocationInfo(normalizedIp, resultCity, regionAddress);
        } catch (Exception e) {
            return new LocationInfo(normalizedIp, "北京", "IP归属地暂时无法获取");
        }
    }

    public String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isUnknown(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (isUnknown(ip)) {
            ip = request.getRemoteAddr();
        }
        return normalizeIp(ip);
    }

    private boolean isUnknown(String ip) {
        return ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip);
    }

    private boolean isLocalIp(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "localhost".equals(ip);
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "127.0.0.1";
        }

        String normalized = ip.trim();
        int commaIndex = normalized.indexOf(',');
        if (commaIndex >= 0) {
            normalized = normalized.substring(0, commaIndex).trim();
        }

        return normalized.isEmpty() ? "127.0.0.1" : normalized;
    }

    private String readText(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return "";
        }

        String value = fieldNode.asText();
        return value == null ? "" : value.trim();
    }

    private String buildRegionAddress(String province, String city, String region, String addr, String fallbackCity) {
        List<String> parts = new ArrayList<>();

        if (!province.isEmpty()) {
            parts.add(province);
        }
        if (!city.isEmpty() && !city.equals(province)) {
            parts.add(city);
        }
        if (!region.isEmpty() && !region.equals(city)) {
            parts.add(region);
        }

        String joined = String.join(" ", parts).trim();
        if (!addr.isEmpty() && (joined.isEmpty() || !addr.contains(joined))) {
            joined = joined.isEmpty() ? addr : joined + " " + addr;
        }

        return joined.isEmpty() ? fallbackCity : joined;
    }
}
